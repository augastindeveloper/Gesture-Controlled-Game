package com.gesture;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.highgui.HighGui;
import org.opencv.videoio.VideoCapture;

public class WebCamConnectionClass {

    public static void main(String[] args) {
    System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    VideoCapture capture = new VideoCapture(0);

    if (!capture.isOpened()) {
        System.out.println("Error: Cannot open the camera!");
        return;
    }

    Mat frame = new Mat();

    while (true) {
        if (capture.read(frame)) {
            HighGui.imshow("Webcam Feed", frame);
        }

        if (HighGui.waitKey(30) == 'q') {
            break;
        }
    }
    capture.release();
    HighGui.destroyAllWindows();
}
}
