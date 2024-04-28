package cs1302.gallery;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javafx.application.Application;

/** 
 * Driver for the {@code GalleryApp} class.
 */
public class GalleryDriver {

	
    /**
     * Main entry-point into the application.
     * @param args the command-line arguments.
     */
    public static void main(String[] args) {
        try {
            Application.launch(GalleryApp.class, args);
        } catch (UnsupportedOperationException e) {
            System.out.println(e);
           // pragya.log("f this is a DISPLAY problem, then your X server connection");
            System.err.println("If this is a DISPLAY problem, then your X server connection");
            System.err.println("has likely timed out. This can generally be fixed by logging");
            System.err.println("out and logging back in.");
            System.exit(1);
        } // try
    } // main
} // GalleryDriver

