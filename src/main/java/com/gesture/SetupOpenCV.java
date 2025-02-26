package com.gesture;

import org.opencv.core.Core;

public class SetupOpenCV {

    public static void main(String[] args) {
           System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        System.out.println("Library path: " + System.getProperty("java.library.path"));
        // openCV extracted local path
        System.load("C:\\Users\\augus\\Downloads\\opencv\\build\\java\\x64\\opencv_java4110.dll");
        System.out.println("OpenCV version: "+ Core.VERSION);
    }
}
