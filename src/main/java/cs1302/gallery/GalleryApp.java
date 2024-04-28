package cs1302.gallery;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.ImageView;
import javafx.scene.image.Image;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;

/**
 * Represents an iTunes Gallery App.
 */
/**
 * @author Shivam Mishra
 */
public class GalleryApp extends Application {

	/** HTTP client. */
	public static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2)
			.followRedirects(HttpClient.Redirect.NORMAL) // always redirects, except from HTTPS to HTTP
			.build(); // builds and returns a HttpClient object

	/** Google {@code Gson} object for parsing JSON-formatted strings. */
	public static Gson GSON = new GsonBuilder().setPrettyPrinting() // enable nice output when printing
			.create(); // builds and returns a Gson object

	private Stage stage;
	private Scene scene;
	private VBox root;
	private HBox searchBar;
	private Button playPauseButton;
	private Label searchLabel;
	private TextField queryTermField;
	private ComboBox<String> mediaTypeDropdown;
	private Button getImages;
	private TextFlow messageBar;
	private FlowPane mainContent;
	private HBox footer;
	private ProgressBar progressBar;
	private Label copyrightText;
	private static final String DEFAULT_STRING = "daft punk";
	private static final int IMAGE_COUNT = 20;
	private static final String LIMIT = "200"; // limit on API to restrict response size
	private List<ImageView> images = new ArrayList<ImageView>(); //List to store the images after downloading from URIs
	private boolean isPlaying = true;
	private Thread randomReplacement;

	/**
	 * Constructs a {@code GalleryApp} object}.
	 */
	public GalleryApp() {
		this.stage = null;
		this.scene = null;
		this.root = new VBox(5);
		this.searchBar = new HBox(5);
		this.playPauseButton = new Button("Play");
		this.searchLabel = new Label("Search:");
		this.queryTermField = new TextField(DEFAULT_STRING);
		this.mediaTypeDropdown = new ComboBox<>();
		this.getImages = new Button("Get Images");
		this.messageBar = new TextFlow();
		this.mainContent = new FlowPane();
		this.footer = new HBox(5);
		this.progressBar = new ProgressBar();
		this.copyrightText = new Label("Images provided by iTunes Search API.");
	} // GalleryApp

	/** {@inheritDoc} */
	@Override
	public void init() {
		// feel free to modify this method
		System.out.println("init() called");
		HBox.setHgrow(this.queryTermField, Priority.ALWAYS);
		this.searchLabel.setStyle("-fx-font-size: 15px;");
		mediaTypeDropdown.getItems().addAll("music", "movie", "podcast", "musicVideo", "audiobook", "shortFilm",
				"tvShow", "software", "ebook", "all");
		mediaTypeDropdown.setValue("music");
		this.progressBar.setProgress(0);
		this.progressBar.setMinWidth(420);
		this.searchBar.getChildren().addAll(this.playPauseButton, this.searchLabel, this.queryTermField,
				this.mediaTypeDropdown, this.getImages);
		this.messageBar.getChildren()
				.add(new Text("Type in a term, select a media type, then click the Get Images button."));
		this.footer.getChildren().addAll(this.progressBar, this.copyrightText);
		this.root.getChildren().addAll(this.searchBar, this.messageBar, this.mainContent, this.footer);
		// actions
		this.playPauseButton.setDisable(true);
		this.getImages.setOnAction(
				event -> this.loadContent(this.queryTermField.getText(), this.mediaTypeDropdown.getValue()));

		this.playPauseButton.setOnAction(event -> this.playPause(this.playPauseButton));
	} // init

	/** {@inheritDoc} */
	@Override
	public void start(Stage stage) {
		this.stage = stage;
		this.scene = new Scene(this.root, 640, 560);
		this.stage.setOnCloseRequest(event -> Platform.exit());
		this.stage.setTitle("My iTunes");
		Image icon = new Image("file:resources/icon.png");
		this.stage.getIcons().add(icon);//added icon to the application
		this.stage.setScene(this.scene);
		this.stage.sizeToScene();
		this.stage.show();
		//Platform.runLater(() -> this.stage.setResizable(false));
		Platform.runLater(() -> this.queryTermField.requestFocus());
		this.defaultContent();
	} // start

	/** {@inheritDoc} */
	@Override
	public void stop() {
		// feel free to modify this method
		System.out.println("stop() called");
		this.playPauseButton.setText("Pause");
		this.playPause(playPauseButton); // In case application is closed in play mode, random replacement thread should be stopped.
	} // stop

	
	/**
	 * This method is called when get Images button is clicked.
	 * It takes the searched text and type of media as input and then download the data
	 * from iTunes API and update images in the application.
	 * @param searchText
	 * @param searchType
	 */
	private void loadContent(String searchText, String searchType) {
		this.onClickGetImages(); // screen changes when getImages button clicked
		HttpResponse<String> response = this.getApiResponse(searchText, searchType); // get the response from iTunes API
		System.out.println(response.request().toString());
		Set<String> imageLinks = this.getImageUriSet(response);// get the distinct image URIs from response
		
		Task<Void> task = new Task<Void>() { //Start the downloading task
			protected Void call() {
				try {
					if (imageLinks.size() < 21) {
						showAlert("Error", "Error", "URL: " + response.request().toString() + "\n" + "Exception: " + imageLinks.size()
								+ " distinct results found, but 21 or more are needed.");
						System.out.println("Going to cancel the task");
						cancel(); // cancel the downloading task if less than 21 distinct URIs available
					}
					else
					{
						images.clear(); // clear the existing images from list
						Iterator<String> iterator = imageLinks.iterator();
						int ct=1;
						while (iterator.hasNext()) {
							String imageUrl = iterator.next();
							ImageView img = new ImageView(new Image(imageUrl));
							img.setFitWidth(128);
							img.setFitHeight(120);
							images.add(img);	
							updateProgress(ct, imageLinks.size());
							ct++;
						}
						Platform.runLater(() -> {
							System.out.println("Called runLater to add images in screen");
							mainContent.getChildren().clear();
							for (int j = 0; j < IMAGE_COUNT; j++) {
								System.out.println("Adding images - " + j);
								mainContent.getChildren().add(images.get(j));
							}
							playPauseButton.setDisable(false);
							getImages.setDisable(false);
							messageBar.getChildren().clear();
							messageBar.getChildren().add(new Text(response.request().toString()));
						});
					}
					System.out.println("at the end of try");
				} catch (Exception e) {
					showAlert("Error", "Error", e.toString());
				} 
				return null;
			}
		};

		progressBar.progressProperty().bind(task.progressProperty());
		Thread thread = new Thread(task);
		thread.setDaemon(true);
		thread.start();

		// Unbind progress bar and set its value to 1.0 when task is cancelled
		task.setOnCancelled(event -> {
			progressBar.progressProperty().unbind();
			progressBar.setProgress(1.0);
		});

	}

	/**
	 * This method is reponsible to generate the initial screen when application is 
	 * loaded using default.png provided
	 */
	private void defaultContent() {
		mainContent.getChildren().clear();
		for (int i = 0; i < IMAGE_COUNT; i++) {
			ImageView imageView = new ImageView(new Image("file:resources/default.png"));
			imageView.setFitWidth(128); // Set image width
			imageView.setFitHeight(120); // Set image height
			this.mainContent.getChildren().add(imageView); //add images in 
		}
	}
	
	/**
	 * This method is reponsible to update screen elements when getImages button is clicked.
	 * When a user click the "Get Images" button :
	 * (i) the Get Images buttons should be disabled
	 * (ii) Instruction should be replaced with the "Getting images..."
	 * (iii) the play/pause buttons should be disabled
	 * (iv) If random replacement is on it should stop
	 */
	private void onClickGetImages() {
		
		this.getImages.setDisable(true); // Disable the get images button
		this.messageBar.getChildren().clear(); //clear the message bar
		this.messageBar.getChildren().add(new Text("Getting images...")); // update the message bar
		this.playPauseButton.setDisable(true);// Disable the play/pause button
		this.playPauseButton.setText("Pause"); // update button text to pause
		this.playPause(playPauseButton); // reset play/pause button to play
		if(randomReplacement!=null) {randomReplacement.interrupt();}
	}
	
	/**
	 * 
	 * This method interacts with the API and returns response based upon request.
	 * @param searchText
	 * @param searchType
	 * @return HttpResponse<String>
	 */
	private HttpResponse<String> getApiResponse(String searchText, String searchType) {
		
		String term = URLEncoder.encode(searchText, StandardCharsets.UTF_8); // encoding searched text
		String limit = URLEncoder.encode(LIMIT, StandardCharsets.UTF_8); // encoding limit value
		String type = URLEncoder.encode(searchType, StandardCharsets.UTF_8); // encoding search type
		String query = String.format("?term=%s&limit=%s&media=%s", term, limit, type); // creating a query for API

		HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://itunes.apple.com/search" + query))
				.build();
		//System.out.println(request.toString());
		
		HttpResponse<String> response;
		try {
			response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
			//System.out.println(response.body());
		} catch (Exception e) {
			response=null;
			showAlert("Error", "Error", e.toString());
		} 
		
		return response;
	}

	/**
	 * 
	 * This method takes response from the API as input and using GSON, extracts distinct image URIs.
	 * @param response
	 * @return Set<String>
	 */
	private Set<String> getImageUriSet(HttpResponse<String> response) {
		
		ItunesResponse res = GSON.fromJson(response.body(), ItunesResponse.class);
		Set<String> imageLinks = new HashSet<>(); // to create a set of distinct image URIs
		
		for (int i = 0; i < res.results.length; i++) {
			if (!imageLinks.contains(res.results[i].artworkUrl100)) {
				imageLinks.add(res.results[i].artworkUrl100);
			}						
		}
		
		System.out.println("The total distinct images URIs are : " + imageLinks.size());
		return imageLinks;
	}
	
	/**
	 * This method is called when play/pause button is clicked.
	 * If button is in play mode, it creates another thread to swap images from the screen 
	 * every 2 seconds randomly.
	 * If button is in pause mode, it stops the random replacement.
	 * @param playPauseButton
	 */
	private void playPause(Button playPauseButton) {

		System.out.println("Clicked on play/pause button : " + playPauseButton.getText());
		if (playPauseButton.getText().equals("Play")) {
			this.playPauseButton.setText("Pause");
			isPlaying = true; // to activate the while loop to start the random replacement
			randomReplacement = new Thread(() -> {
				this.randomReplacement();
			});
			randomReplacement.start();
		} else {
			isPlaying = false;
			if(randomReplacement!=null) {randomReplacement.interrupt();}
			this.playPauseButton.setText("Play");
		}
	}
	
	/**
	 * This method performs random replacement in the main screen 
	 * every 2 seconds between the image available on screen and image not available on screen.
	 */
	private void randomReplacement() {
		try {
			while (isPlaying) {
				Thread.currentThread().setName("Random_placement");
				Thread.sleep(2000);
				if (images.size() > 0) {
					int randomIndexUnder20 = new Random().nextInt(IMAGE_COUNT);
					int randomIndexOver20 = 20 + new Random().nextInt(images.size() - IMAGE_COUNT);
					System.out.println(randomIndexUnder20 + "  --  " + randomIndexOver20);
					ImageView tmp = images.get(randomIndexOver20);
					images.set(randomIndexOver20, images.get(randomIndexUnder20));
					images.set(randomIndexUnder20, tmp);
					Platform.runLater(() -> this.mainContent.getChildren().set(randomIndexUnder20,
							images.get(randomIndexUnder20)));
				}
			}
		} catch (InterruptedException e) {
			System.out.println("Stopped random replacement thread by JavaFx application thread.");
		} catch (Exception e) {
			showAlert("Error", "Error", e.toString());
		}

	}
	
	/**
	 * This method is responsible for the appropriate error message on the screen
	 * if any exception occurred in a flow.
	 * @param title
	 * @param headerText
	 * @param contentText
	 */
	private void showAlert(String title, String headerText, String contentText) {
		Platform.runLater(() -> {
			messageBar.getChildren().clear();
			messageBar.getChildren().add(new Text("Last attempt to get images failed..."));
			playPauseButton.setDisable(false);
			getImages.setDisable(false);
			Alert alert = new Alert(Alert.AlertType.ERROR);
			alert.setTitle(title);
			alert.setHeaderText(headerText);
			alert.setContentText(contentText);
			alert.showAndWait();
		});
	}
} // GalleryApp
