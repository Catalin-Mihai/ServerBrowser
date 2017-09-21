package com.msc.serverbrowser.gui.controllers.implementations.serverlist;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.regex.PatternSyntaxException;

import com.github.plushaze.traynotification.animations.Animations;
import com.github.plushaze.traynotification.notification.NotificationTypeImplementations;
import com.github.plushaze.traynotification.notification.TrayNotificationBuilder;
import com.msc.serverbrowser.data.FavouritesController;
import com.msc.serverbrowser.data.entites.Player;
import com.msc.serverbrowser.data.entites.SampServer;
import com.msc.serverbrowser.gui.controllers.interfaces.ViewController;
import com.msc.serverbrowser.logging.Logging;
import com.msc.serverbrowser.util.ServerUtility;
import com.msc.serverbrowser.util.basic.StringUtility;
import com.msc.serverbrowser.util.samp.GTAController;
import com.msc.serverbrowser.util.samp.SampQuery;
import com.msc.serverbrowser.util.windows.OSUtility;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.MouseButton;
import javafx.util.Duration;

/**
 * @since 02.07.2017
 */
public class BasicServerListController implements ViewController
{
	private static final String									RETRIEVING						= "Retrieving...";

	private final ObjectProperty<Predicate<? super SampServer>>	filterProperty					= new SimpleObjectProperty<>();

	@FXML
	private TextField											addressTextField;

	private final static StringProperty							serverAddressProperty			= new SimpleStringProperty();

	/**
	 * This Table contains all available servers / favourite servers, depending on
	 * the active view.
	 */
	@FXML
	protected TableView<SampServer>								serverTable;

	/**
	 * Displays the number of active players on all Servers in {@link #serverTable}.
	 */
	@FXML
	protected Label												playerCount;
	/**
	 * Displays the amount of all slots on all Servers in {@link #serverTable}.
	 */
	@FXML
	protected Label												slotCount;
	/**
	 * Number of servers in {@link #serverTable}.
	 */
	@FXML
	protected Label												serverCount;
	@FXML
	private TextField											serverAddress;
	@FXML
	private Label												serverLagcomp;
	@FXML
	private Label												serverPing;
	@FXML
	private Label												serverPassword;
	@FXML
	private Label												mapLabel;
	@FXML
	private Hyperlink											websiteLink;

	@FXML
	private TableView<Player>									playerTable;
	@FXML
	private TableColumn<SampServer, String>						columnPlayers;

	/**
	 * When clicked, all selected servers will be added to favourites.
	 */
	protected MenuItem											addToFavouritesMenuItem			= new MenuItem(
			"Add to Favourites");
	/**
	 * When clicked, all selected servers will be removed from favourites.
	 */
	protected MenuItem											removeFromFavouritesMenuItem	= new MenuItem(
			"Remove from Favourites");
	private final MenuItem										visitWebsiteMenuItem			= new MenuItem(
			"Visit Website");
	private final MenuItem										connectMenuItem					= new MenuItem(
			"Connect to Server");
	private final MenuItem										copyIpAddressAndPortMenuItem	= new MenuItem(
			"Copy IP Address and Port");
	private final ContextMenu									menu							= new ContextMenu(
			connectMenuItem, new SeparatorMenuItem(), addToFavouritesMenuItem, removeFromFavouritesMenuItem,
			copyIpAddressAndPortMenuItem, visitWebsiteMenuItem);

	@FXML
	private CheckBox											regexCheckBox;
	@FXML
	private TextField											nameFilter;
	@FXML
	private TextField											modeFilter;
	@FXML
	private TextField											languageFilter;
	@FXML
	private ComboBox<String>									versionFilter;

	/**
	 * Holds all servers that might be displayed in {@link #serverTable}.
	 */
	protected ObservableList<SampServer>						servers							= FXCollections
			.observableArrayList();

	private static Thread										serverInfoUpdateThread;

	/**
	 * Empty Constructor.
	 */
	protected BasicServerListController()
	{
		// Prevent instantiation from outside.
	}

	@Override
	public void initialize()
	{
		final FilteredList<SampServer> filteredServers = new FilteredList<>(servers);
		final SortedList<SampServer> sortedServers = new SortedList<>(filteredServers);

		filteredServers.predicateProperty().bind(filterProperty);
		sortedServers.comparatorProperty().bind(serverTable.comparatorProperty());
		addressTextField.textProperty().bindBidirectional(serverAddressProperty);

		setPlayerComparator();
		addServerUpdateListener();
		setTableRowFactory();

		serverTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		serverTable.setItems(sortedServers);
	}

	private void setPlayerComparator()
	{
		columnPlayers.setComparator((o1, o2) ->
		{
			final int p1 = Integer.parseInt(o1.replaceAll("[/](.*)", ""));
			final int p2 = Integer.parseInt(o2.replaceAll("[/](.*)", ""));

			return p1 < p2 ? -1 : p1 == p2 ? 0 : 1;
		});
	}

	private void setTableRowFactory()
	{
		serverTable.setRowFactory(facotry ->
		{
			final TableRow<SampServer> row = new TableRow<>();

			row.setOnMouseClicked(clicked ->
			{
				menu.hide();
				final List<SampServer> serverList = serverTable.getSelectionModel().getSelectedItems();
				final SampServer rowItem = row.getItem();

				if (serverTable.getSelectionModel().getSelectedIndices().contains(row.getIndex()))
				{
					if (!serverList.isEmpty() && clicked.getButton().equals(MouseButton.SECONDARY))
					{
						displayMenu(serverList, clicked.getScreenX(), clicked.getScreenY());
					}
				}
				else
				{
					if (Objects.nonNull(rowItem))
					{
						serverTable.getSelectionModel().select(rowItem);
						if (clicked.getButton().equals(MouseButton.SECONDARY))
						{
							displayMenu(Arrays.asList(rowItem), clicked.getScreenX(), clicked.getScreenY());
						}
					}
					else
					{
						serverTable.getSelectionModel().clearSelection();
					}
				}

			});

			return row;
		});
	}

	private void addServerUpdateListener()
	{
		serverTable.getSelectionModel().getSelectedCells().addListener((InvalidationListener) changed ->
		{
			if (serverTable.getSelectionModel().getSelectedIndices().size() == 1)
			{
				final SampServer selectedServer = serverTable.getSelectionModel().getSelectedItem();
				if (Objects.nonNull(selectedServer))
				{
					updateServerInfo(selectedServer);
				}
			}
			else
			{
				playerTable.getItems().clear();
				playerTable.setPlaceholder(new Label(""));
				serverAddress.setText("");
				serverLagcomp.setText("");
				serverPing.setText("");
				serverPassword.setText("");

				killServerLookupThread();
			}
		});
	}

	private static void killServerLookupThread()
	{
		if (Objects.nonNull(serverInfoUpdateThread))
		{
			serverInfoUpdateThread.interrupt();
		}
	}

	@FXML
	private void onClickAddToFavourites()
	{
		final String address = addressTextField.getText();
		if (Objects.nonNull(address) && !address.isEmpty())
		{
			final String[] ipAndPort = addressTextField.getText().split("[:]");
			if (ipAndPort.length == 1)
			{
				addServerToFavourites(ipAndPort[0], 7777);
			}
			else if (ipAndPort.length == 2 && ServerUtility.isPortValid(ipAndPort[1]))
			{
				addServerToFavourites(ipAndPort[0], Integer.parseInt(ipAndPort[1]));
			}
			else
			{
				new TrayNotificationBuilder()
						.type(NotificationTypeImplementations.ERROR)
						.title("Add to favourites")
						.message("Can't add to favourites, server address is invalid.")
						.animation(Animations.POPUP)
						.build().showAndDismiss(Duration.seconds(10));
			}
		}
	}

	private void addServerToFavourites(final String ip, final int port)
	{
		final SampServer newServer = FavouritesController.addServerToFavourites(ip, port);
		if (!servers.contains(newServer))
		{
			servers.add(newServer);
		}
	}

	@FXML
	private void onClickConnect()
	{
		final String[] ipAndPort = Optional.ofNullable(addressTextField.getText()).orElse("").split("[:]");
		if (ipAndPort.length == 1)
		{
			tryToConnect(ipAndPort[0], 7777);
		}
		else if (ipAndPort.length == 2 && ServerUtility.isPortValid(ipAndPort[1]))
		{
			tryToConnect(ipAndPort[0], Integer.parseInt(ipAndPort[1]));
		}
		else
		{
			showCantConnectToServerError();
		}
	}

	private static void showCantConnectToServerError()
	{
		new TrayNotificationBuilder()
				.type(NotificationTypeImplementations.ERROR)
				.title("Can't connect to Server")
				.message("The address that you have entered, doesn't seem to be valid.")
				.animation(Animations.POPUP)
				.build().showAndDismiss(Duration.seconds(10));
	}

	@FXML
	private void onFilterSettingsChange()
	{
		filterProperty.set(server ->
		{
			boolean nameFilterApplies = true;
			boolean modeFilterApplies = true;
			boolean languageFilterApplies = true;
			boolean versionFilterApplies = true;

			if (!versionFilter.getSelectionModel().isEmpty())
			{
				final String versionFilterSetting = versionFilter.getSelectionModel().getSelectedItem().toString()
						.toLowerCase();

				// TODO(MSC) Only necessary because i don't retrieve the version when querying
				// southclaws api. I should request a change in the api.
				final String serverVersion = Objects.isNull(server.getVersion()) ? "" : server.getVersion();
				versionFilterApplies = serverVersion.toLowerCase().contains(versionFilterSetting);
			}

			final String nameFilterSetting = nameFilter.getText().toLowerCase();
			final String modeFilterSetting = modeFilter.getText().toLowerCase();
			final String languageFilterSetting = languageFilter.getText().toLowerCase();

			final String hostname = server.getHostname().toLowerCase();
			final String mode = server.getMode().toLowerCase();
			final String language = server.getLanguage().toLowerCase();

			if (regexCheckBox.isSelected())
			{
				nameFilterApplies = regexFilter(hostname, nameFilterSetting);
				modeFilterApplies = regexFilter(mode, modeFilterSetting);
				languageFilterApplies = regexFilter(language, languageFilterSetting);
			}
			else
			{
				nameFilterApplies = hostname.contains(nameFilterSetting);
				modeFilterApplies = mode.contains(modeFilterSetting);
				languageFilterApplies = language.contains(languageFilterSetting);
			}

			return nameFilterApplies && modeFilterApplies && versionFilterApplies && languageFilterApplies;
		});

		updateGlobalInfo();
	}

	private static boolean regexFilter(final String toFilter, final String filterSetting)
	{
		if (!filterSetting.isEmpty())
		{
			try
			{
				if (!toFilter.matches(filterSetting))
				{
					return false;
				}
			}
			catch (@SuppressWarnings("unused") final PatternSyntaxException exception)
			{
				return false;
			}
		}

		return true;
	}

	/**
	 * Displays the context menu for server entries.
	 *
	 * @param serverList
	 *            The list of servers that the context menu actions will affect
	 * @param posX
	 *            X coordinate
	 * @param posY
	 *            Y coodrinate
	 */
	protected void displayMenu(final List<SampServer> serverList, final double posX, final double posY)
	{
		/*
		 * TODO(MSC) Refactor;
		 *
		 * All this stuff shouldn't be set every time the user requests a menu, instead,
		 * this
		 * should be set once when initializing the controller.
		 */

		final boolean sizeEqualsOne = serverList.size() == 1;

		connectMenuItem.setVisible(sizeEqualsOne);
		menu.getItems().get(1).setVisible(sizeEqualsOne); // Separator
		copyIpAddressAndPortMenuItem.setVisible(sizeEqualsOne);
		visitWebsiteMenuItem.setVisible(sizeEqualsOne);

		menu.setOnAction(action ->
		{
			final MenuItem clickedItem = (MenuItem) action.getTarget();

			if (clickedItem.equals(connectMenuItem))
			{
				final SampServer server = serverList.get(0);
				tryToConnect(server.getAddress(), server.getPort());
			}
			else if (clickedItem.equals(visitWebsiteMenuItem))
			{
				final SampServer server = serverList.get(0);
				OSUtility.browse(server.getWebsite());
			}
			else if (clickedItem.equals(addToFavouritesMenuItem))
			{
				serverList.forEach(FavouritesController::addServerToFavourites);
			}
			else if (clickedItem.equals(removeFromFavouritesMenuItem))
			{
				serverList.forEach(FavouritesController::removeServerFromFavourites);
				servers.removeAll(serverList);
			}
			else if (clickedItem.equals(copyIpAddressAndPortMenuItem))
			{
				final SampServer server = serverList.get(0);
				final String addressAndPort = server.getAddress() + ":" + server.getPort();
				final StringSelection stringSelection = new StringSelection(addressAndPort);
				final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
				clipboard.setContents(stringSelection, null);
			}
		});

		menu.show(serverTable, posX, posY);
	}

	/**
	 * Connects to a server, depending on if it is passworded, the user will be
	 * asked to enter a
	 * password. If the server is not reachable the user can not connect.
	 *
	 * @param address
	 *            server address
	 * @param port
	 *            server port
	 */
	private static void tryToConnect(final String address, final Integer port)
	{
		try (final SampQuery query = new SampQuery(address, port))
		{
			final Optional<String[]> serverInfo = query.getBasicServerInfo();

			if (serverInfo.isPresent() && StringUtility.stringToBoolean(serverInfo.get()[0]))
			{
				final TextInputDialog dialog = new TextInputDialog();
				dialog.setTitle("Connect to Server");
				dialog.setHeaderText("Enter the servers password (Leave empty if u think there is none).");

				final Optional<String> result = dialog.showAndWait();
				result.ifPresent(password -> GTAController.connectToServer(address + ":" + port, password));
			}
			else
			{
				GTAController.connectToServer(address + ":" + port);
			}
		}
		catch (final IOException exception)
		{
			Logging.log(Level.WARNING, "Couldn't connect to server.", exception);
			showCantConnectToServerError();
		}
	}

	/**
	 * TODO(MSC) Burn it before it lays eggs. Hans, get the Flammenwerfer. Updates
	 * the data that the
	 * {@link SampServer} holds and displays the correct values on the UI.
	 *
	 * @param server
	 *            the {@link SampServer} object to update locally
	 */
	protected void updateServerInfo(final SampServer server)
	{
		setVisibleDetailsToRetrieving(server);
		killServerLookupThread();

		serverInfoUpdateThread = new Thread(() ->
		{
			try (final SampQuery query = new SampQuery(server.getAddress(), server.getPort()))
			{
				final Optional<String[]> infoOptional = query.getBasicServerInfo();
				final Optional<Map<String, String>> serverRulesOptional = query.getServersRules();

				if (infoOptional.isPresent() && serverRulesOptional.isPresent())
				{
					final String[] info = infoOptional.get();
					final Map<String, String> serverRules = serverRulesOptional.get();

					final int activePlayers = Integer.parseInt(info[1]);
					final int maxPlayers = Integer.parseInt(info[2]);

					server.setPassworded(StringUtility.stringToBoolean(info[0]));
					server.setPlayers(activePlayers);
					server.setMaxPlayers(maxPlayers);
					server.setHostname(info[3]);
					server.setMode(info[4]);
					server.setLanguage(info[5]);
					server.setWebsite(serverRules.get("weburl"));
					server.setVersion(serverRules.get("version"));
					server.setLagcomp(serverRules.get("lagcomp"));
					server.setMap(serverRules.get("mapname"));

					final ObservableList<Player> playerList = FXCollections.observableArrayList();
					query.getBasicPlayerInfo().ifPresent(players -> playerList.addAll(players));
					final long ping = query.getPing();

					applyData(server, playerList, ping);
					FavouritesController.updateServerData(server);
				}
			}
			catch (@SuppressWarnings("unused") final IOException exception)
			{
				if (!serverInfoUpdateThread.isInterrupted())
				{
					Platform.runLater(() -> displayOfflineInformation());
				}
			}
		});

		serverInfoUpdateThread.start();
	}

	private void setVisibleDetailsToRetrieving(final SampServer server)
	{
		playerTable.getItems().clear();
		playerTable.setPlaceholder(new Label(RETRIEVING));
		serverAddress.setText(server.getAddress() + ":" + server.getPort());
		serverLagcomp.setText(RETRIEVING);
		serverPing.setText(RETRIEVING);
		serverPassword.setText(RETRIEVING);
		mapLabel.setText(RETRIEVING);
		websiteLink.setText(RETRIEVING);
		websiteLink.setUnderline(false);
		websiteLink.setOnAction(null);
	}

	private void applyData(final SampServer server, final ObservableList<Player> playerList,
			final long ping)
	{
		if (!serverInfoUpdateThread.isInterrupted())
		{
			Platform.runLater(() ->
			{
				serverPassword.setText(server.isPassworded() ? "Yes" : "No");
				serverPing.setText(String.valueOf(ping));
				mapLabel.setText(server.getMap());
				websiteLink.setText(server.getWebsite());
				playerTable.setItems(playerList);

				final String websiteToLower = server.getWebsite().toLowerCase();
				final String websiteFixed = StringUtility.fixUrlIfNecessary(websiteToLower);

				if (StringUtility.isValidURL(websiteFixed))
				{
					websiteLink.setUnderline(true);
					websiteLink.setOnAction(__ -> OSUtility.browse(server.getWebsite()));
				}

				final boolean noPlayers = playerList.isEmpty();
				if (noPlayers)
				{
					playerTable.setPlaceholder(new Label("Server is empty"));

					if (server.getPlayers() >= 100)
					{
						final String TOO_MUCH_PLAYERS = "Can't retrieve players, if there are more than 100.";
						final Label label = new Label(TOO_MUCH_PLAYERS);
						label.setWrapText(true);
						label.setAlignment(Pos.CENTER);
						playerTable.setPlaceholder(label);
					}
				}

				serverLagcomp.setText(server.getLagcomp());
				updateGlobalInfo();
			});
		}
	}

	private void displayOfflineInformation()
	{
		serverPing.setText("Server Offline");
		serverPassword.setText("");
		mapLabel.setText("");
		serverLagcomp.setText("");
		websiteLink.setText("");
		// Not using setVisible because i dont want the items to resize or anything
		websiteLink.setOnAction(null);
		playerTable.setPlaceholder(new Label("Server is offline."));
	}

	/**
	 * Updates the {@link Label Labels} at the bottom of the Serverlist view.
	 */
	protected void updateGlobalInfo()
	{
		int playersPlaying = 0;
		int maxSlots = 0;

		for (final SampServer server : servers)
		{
			playersPlaying += server.getPlayers();
			maxSlots += server.getMaxPlayers();
		}

		serverCount.setText(String.valueOf(serverTable.getItems().size()));
		playerCount.setText(String.valueOf(playersPlaying));
		slotCount.setText(String.valueOf(maxSlots - playersPlaying));
	}

	@Override
	public void onClose()
	{
		killServerLookupThread();
	}
}