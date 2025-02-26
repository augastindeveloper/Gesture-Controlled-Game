package com.gesture;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

public class GestureControlApp extends Application {
    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;

    private double playerX = WIDTH / 2;
    private double playerY = HEIGHT - 150;
    private volatile boolean running = true;

    private final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();

    private final VideoCapture capture = new VideoCapture();
    private final Mat frame = new Mat();
    private final Canvas gameCanvas = new Canvas(WIDTH, HEIGHT);
    private final Canvas webcamCanvas = new Canvas(WIDTH, HEIGHT);

    private Image idleSprite;
    private Image walkSprite;
    private Image runSprite;
    private Image jumpSprite;
    private Image currentSprite;

    private int lastFingers = 0;
    private long lastChangeTime = 0;
    private static final long DEBOUNCE_TIME = 500;

    @Override
    public void start(Stage primaryStage) {
        loadSprites();
        currentSprite = idleSprite;

        HBox root = new HBox(new StackPane(webcamCanvas), new StackPane(gameCanvas));
        primaryStage.setScene(new Scene(root));
        primaryStage.setTitle("Gesture-Controlled Game with Webcam");
        primaryStage.show();

        cameraExecutor.execute(this::processCameraFeed);
    }

    private void loadSprites() {
        idleSprite = loadImage("/idle.png");
        walkSprite = loadImage("/walk.png");
        runSprite = loadImage("/run.png");
        jumpSprite = loadImage("/jump.png");
    }

    private Image loadImage(String path) {
        return Optional.ofNullable(getClass().getResource(path))
                .map(url -> new Image(url.toExternalForm()))
                .orElseThrow(() -> new RuntimeException("Missing sprite: " + path));
    }

    private void processCameraFeed() {
        if (!capture.open(0)) {
            System.err.println("Error: Cannot open the camera!");
            return;
        }

        while (running) {
            if (capture.read(frame)) {
                int fingers = detectFingers(frame.clone());
                Platform.runLater(() -> {
                    updateGame(fingers);
                    updateWebcamFeed();
                });
            }
        }
        capture.release();
    }

    private void updateWebcamFeed() {
        if (!frame.empty()) {
            Mat displayFrame = frame.clone();
            drawHandContour(displayFrame);
            WritableImage image = convertMatToImage(displayFrame);

            GraphicsContext gc = webcamCanvas.getGraphicsContext2D();
            gc.clearRect(0, 0, WIDTH, HEIGHT);
            gc.drawImage(image, 0, 0, WIDTH, HEIGHT);
            gc.fillText("Fingers: " + lastFingers, 20, 30);
        }
    }

    private void drawHandContour(Mat frame) {
        List<MatOfPoint> contours = new ArrayList<>();
        Mat mask = getSkinMask(frame);
        Imgproc.findContours(mask, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        getLargestContour(contours).ifPresent(largestContour ->
                Imgproc.drawContours(frame, Collections.singletonList(largestContour), -1, new Scalar(0, 255, 0), 2));
    }

    private Mat getSkinMask(Mat frame) {
        Mat hsv = new Mat();
        Imgproc.cvtColor(frame, hsv, Imgproc.COLOR_BGR2HSV);
        Mat mask = new Mat();
        Core.inRange(hsv, new Scalar(0, 20, 70), new Scalar(20, 255, 255), mask);
        Imgproc.GaussianBlur(mask, mask, new Size(5, 5), 0);
        return mask;
    }

    private WritableImage convertMatToImage(Mat mat) {
        int width = mat.width(), height = mat.height();
        WritableImage img = new WritableImage(width, height);
        PixelWriter pw = img.getPixelWriter();
        ByteBuffer buffer = ByteBuffer.allocate(width * height * 3);
        mat.get(0, 0, buffer.array());
        pw.setPixels(0, 0, width, height, PixelFormat.getByteRgbInstance(), buffer, width * 3);
        return img;
    }

    private Optional<MatOfPoint> getLargestContour(List<MatOfPoint> contours) {
        return contours.stream()
                .max(Comparator.comparingDouble(Imgproc::contourArea));
    }

    private int detectFingers(Mat frame) {
        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(getSkinMask(frame), contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        return getLargestContour(contours)
                .map(contour -> {
                    MatOfInt hull = new MatOfInt();
                    Imgproc.convexHull(contour, hull);
                    return countFingers(contour, hull);
                })
                .orElse(0);
    }

    private int countFingers(MatOfPoint contour, MatOfInt hull) {
        MatOfInt4 convexDefects = new MatOfInt4();
        Imgproc.convexityDefects(contour, hull, convexDefects);

        return (int) IntStream.range(0, convexDefects.rows())
                .mapToDouble(i -> convexDefects.get(i, 0)[3])
                .filter(depth -> depth > 10000)
                .count();

    }

    private void updateGame(int fingers) {
        long currentTime = System.currentTimeMillis();
        if (fingers != lastFingers && (currentTime - lastChangeTime) > DEBOUNCE_TIME) {
            lastFingers = fingers;
            lastChangeTime = currentTime;
        }

        double moveSpeed = 1;
        double jumpHeight = 5;

        if (lastFingers == 1) {
            playerX -= moveSpeed;
            currentSprite = walkSprite;
        } else if (lastFingers == 2) {
            playerX += moveSpeed;
            currentSprite = runSprite;
        } else if (lastFingers == 3) {
            playerY -= jumpHeight;
            currentSprite = jumpSprite;
        } else if (lastFingers == 4) {
            playerX -= moveSpeed * 1.2;
            currentSprite = walkSprite;
        } else {
            currentSprite = idleSprite;
        }

        playerX = Math.max(0, Math.min(WIDTH - 500, playerX));
        playerY = Math.max(0, Math.min(HEIGHT - 500, playerY));

        drawGame();
    }

    private void drawGame() {
        GraphicsContext gc = gameCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, WIDTH, HEIGHT);

        double spriteWidth = 400;
        double spriteHeight = 500;

        gc.drawImage(currentSprite, playerX, playerY, spriteWidth, spriteHeight);
    }


    @Override
    public void stop() {
        running = false;
        cameraExecutor.shutdown();
        capture.release();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
