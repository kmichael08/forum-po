package pl.edu.mimuw.forum.ui.controllers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import pl.edu.mimuw.forum.data.Dodawanie;
import pl.edu.mimuw.forum.data.ListaOperacji;
import pl.edu.mimuw.forum.data.Usuwanie;
import pl.edu.mimuw.forum.exceptions.ApplicationException;
import pl.edu.mimuw.forum.ui.bindings.MainPaneBindings;
import pl.edu.mimuw.forum.ui.helpers.DialogHelper;
import pl.edu.mimuw.forum.ui.models.CommentViewModel;
import pl.edu.mimuw.forum.ui.models.NodeViewModel;
import pl.edu.mimuw.forum.ui.tree.ForumTreeItem;
import pl.edu.mimuw.forum.ui.tree.TreeLabel;

/**
 * Kontroler glownego widoku reprezentujacego forum.
 * Widok sklada sie z drzewa zawierajacego wszystkie wezly forum oraz
 * panelu z polami do edycji wybranego wezla.
 * @author konraddurnoga
 */
public class MainPaneController implements Initializable {

	/**
	 * Korzen drzewa-modelu forum.
	 */
	private NodeViewModel document;

	/**
	 * Wiazania stosowane do komunikacji z {@link pl.edu.mimuw.forum.ui.controller.ApplicationController }.
	 */
	private MainPaneBindings bindings;
	
	/**
	 * Widok drzewa forum (wyswietlany w lewym panelu).
	 */
	@FXML
	private TreeView<NodeViewModel> treePane;

	/**
	 * Kontroler panelu wyswietlajacego pola do edycji wybranego wezla w drzewie.
	 */
	@FXML
	private DetailsPaneController detailsController;
	
			
	
	public MainPaneController() {
		bindings = new MainPaneBindings();
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		BooleanBinding nodeSelectedBinding = Bindings.isNotNull(treePane.getSelectionModel().selectedItemProperty());
		bindings.nodeAdditionAvailableProperty().bind(nodeSelectedBinding);
		bindings.nodeRemovaleAvailableProperty()
				.bind(nodeSelectedBinding.and(
						Bindings.createBooleanBinding(() -> getCurrentTreeItem().orElse(null) != treePane.getRoot(),
								treePane.rootProperty(), nodeSelectedBinding)));
		
		bindings.hasChangesProperty().set(true);		// Nalezy ustawic na true w przypadku, gdy w widoku sa zmiany do
														// zapisania i false wpp, w odpowiednim miejscu kontrolera (niekoniecznie tutaj)
														// Spowoduje to dodanie badz usuniecie znaku '*' z tytulu zakladki w ktorej
														// otwarty jest plik - '*' oznacza niezapisane zmiany
		bindings.undoAvailableProperty().set(false);	
		bindings.redoAvailableProperty().set(false);		// Podobnie z undo i redo
	}

	public MainPaneBindings getPaneBindings() {
		return bindings;
	}

	/**
	 * Otwiera plik z zapisem forum i tworzy reprezentacje graficzna wezlow forum.
	 * @param file
	 * @return
	 * @throws ApplicationException
	 * @throws IOException 
	 * @throws ClassNotFoundException 
	 */
	public Node open(File file) throws ApplicationException, IOException, ClassNotFoundException {
		if (file != null) {
		
			String nazwaPliku = file.getAbsolutePath();
			
			XStream xstream = new XStream(new DomDriver("Unicode"));
			xstream.addImplicitCollection(pl.edu.mimuw.forum.data.Node.class, "children", pl.edu.mimuw.forum.data.Node.class);

			Reader rdr = new BufferedReader(new InputStreamReader(new FileInputStream(nazwaPliku), "UTF-8"));
			ObjectInputStream in = xstream.createObjectInputStream(rdr);
			
			pl.edu.mimuw.forum.data.Node wezel = (pl.edu.mimuw.forum.data.Node) in.readObject();
			document = wezel.getModel();
			
			in.close();
			
		} 
		else {
			document = new CommentViewModel("Welcome to a new forum", "Admin");
		}

		/** Dzieki temu kontroler aplikacji bedzie mogl wyswietlic nazwe pliku jako tytul zakladki.
		 * Obsluga znajduje sie w {@link pl.edu.mimuw.forum.ui.controller.ApplicationController#createTab }
		 */

		getPaneBindings().fileProperty().set(file);
	
		return openInView(document);
	}

	/**
	 * Zapisuje aktualny stan forum do pliku.
	 * @throws ApplicationException
	 * @throws IOException 
	 */
	public void save() throws ApplicationException, IOException {
		/**
		 * Obiekt pliku do ktorego mamy zapisac drzewo znajduje sie w getPaneBindings().fileProperty().get()
		 */
		if (document != null) {
			String nazwaPliku = getPaneBindings().fileProperty().get().getAbsolutePath();
		
			PrintWriter pw = new PrintWriter(nazwaPliku, "UTF-8");
			XStream xstream = new XStream(new DomDriver("Unicode"));
			xstream.addImplicitCollection(pl.edu.mimuw.forum.data.Node.class, "children", pl.edu.mimuw.forum.data.Node.class);
			ObjectOutputStream out = xstream.createObjectOutputStream(pw, "Forum");
		
			out.writeObject(document.toNode());
			out.close();
			
			// plik zapisany
			bindings.hasChangesProperty().set(false);
		}
	}
	
	/**
	 * Cofa ostatnio wykonana operacje na forum.
	 * @throws ApplicationException
	 */
	public void undo() throws ApplicationException {
		ListaOperacji.undo();
		if (ListaOperacji.actualPosition() == 0)
			bindings.undoAvailableProperty().set(false);
		else
			bindings.undoAvailableProperty().set(true);
			bindings.redoAvailableProperty().set(true);
	}
	

	/**
	 * Ponawia ostatnia cofnieta operacje na forum.
	 * @throws ApplicationException
	 */
	public void redo() throws ApplicationException {
		ListaOperacji.redo();
		if (ListaOperacji.actualPosition() == ListaOperacji.size())
			bindings.redoAvailableProperty().set(false);
		else 
			bindings.redoAvailableProperty().set(true);
			bindings.undoAvailableProperty().set(true);
	}

	/**
	 * Podaje nowy wezel jako ostatnie dziecko aktualnie wybranego wezla.
	 * @param node
	 * @throws ApplicationException
	 */
	public void addNode(NodeViewModel node) throws ApplicationException {
		getCurrentNode().ifPresent(currentlySelected -> {
			
			// dodajemy operacje dodania do listy
			NodeViewModel przodek = currentlySelected;
						
			ListaOperacji.add(ListaOperacji.actualPosition(), new Dodawanie(przodek, node));
						
			currentlySelected.getChildren().add(node);		// Zmieniamy jedynie model, widok (TreeView) jest aktualizowany z poziomu
															// funkcji nasluchujacej na zmiany w modelu (zob. metode createViewNode ponizej)
			
		});
	}

	/**
	 * Usuwa aktualnie wybrany wezel.
	 */
	public void deleteNode() {
		getCurrentTreeItem().ifPresent(currentlySelectedItem -> {
			TreeItem<NodeViewModel> parent = currentlySelectedItem.getParent();

			NodeViewModel parentModel;
			NodeViewModel currentModel = currentlySelectedItem.getValue();
			if (parent == null) {
				return; // Blokujemy usuniecie korzenia - TreeView bez korzenia jest niewygodne w obsludze
			} else {
				parentModel = parent.getValue();
				
				// dodajemy operacje usuwania do listy
				ListaOperacji.add(ListaOperacji.actualPosition(), new Usuwanie(parentModel, currentModel));
				
				parentModel.getChildren().remove(currentModel); // Zmieniamy jedynie model, widok (TreeView) jest aktualizowany z poziomu
																// funkcji nasluchujacej na zmiany w modelu (zob. metode createViewNode ponizej)
			}
			

		});
	}
	
	// Ustawiamy przyciski modyfikacji
	private void setModificationButtons() {
		if (ListaOperacji.actualPosition() == 0)
			bindings.undoAvailableProperty().set(false);
		else
			bindings.undoAvailableProperty().set(true);
		
		if (ListaOperacji.actualPosition() == ListaOperacji.size())
			bindings.redoAvailableProperty().set(false);
		else 
			bindings.redoAvailableProperty().set(true);

	}
	
	private Node openInView(NodeViewModel document) throws ApplicationException {
		Node view = loadFXML();
				
		treePane.setCellFactory(tv -> {
			try {
				//Do reprezentacji graficznej wezla uzywamy niestandardowej klasy wyswietlajacej 2 etykiety
				// tworzenie nowego widoku
				return new TreeLabel();
			} catch (ApplicationException e) {
				DialogHelper.ShowError("Error creating a tree cell.", e);
				return null;
			}
		});

		ForumTreeItem root = createViewNode(document);
		root.addEventHandler(TreeItem.<NodeViewModel> childrenModificationEvent(), event -> {

			if (event.wasAdded()) { 
				setModificationButtons();
				
			}
			
			if (event.wasRemoved()) {
				setModificationButtons();
			}
			
			// dodanie/ usuniecie wezla
			bindings.hasChangesProperty().set(true);
		});

		treePane.setRoot(root);

		for (NodeViewModel w : document.getChildren()) {
			addToTree(w, root);
		}

		expandAll(root);

		treePane.getSelectionModel().selectedItemProperty()
				.addListener((observable, oldValue, newValue) -> onItemSelected(oldValue, newValue));
		
		return view;
	}
	
	private Node loadFXML() throws ApplicationException {
		FXMLLoader loader = new FXMLLoader();
		loader.setController(this);
		loader.setLocation(getClass().getResource("/fxml/main_pane.fxml"));

		try {
			return loader.load();
		} catch (IOException e) {
			throw new ApplicationException(e);
		}
	}
	
	private Optional<? extends NodeViewModel> getCurrentNode() {
		return getCurrentTreeItem().<NodeViewModel> map(TreeItem::getValue);
	}

	private Optional<TreeItem<NodeViewModel>> getCurrentTreeItem() {
		return Optional.ofNullable(treePane.getSelectionModel().getSelectedItem());
	}

	private void addToTree(NodeViewModel node, ForumTreeItem parentViewNode, int position) {
		ForumTreeItem viewNode = createViewNode(node);

		List<TreeItem<NodeViewModel>> siblings = parentViewNode.getChildren();
		siblings.add(position == -1 ? siblings.size() : position, viewNode);

		node.getChildren().forEach(child -> addToTree(child, viewNode));
	}

	private void addToTree(NodeViewModel node, ForumTreeItem parentViewNode) {
		addToTree(node, parentViewNode, -1);
	}

	private void removeFromTree(ForumTreeItem viewNode) {
		viewNode.removeChildListener();
		TreeItem<NodeViewModel> parent = viewNode.getParent();
		if (parent != null) {
			viewNode.getParent().getChildren().remove(viewNode);
		} else {
			treePane.setRoot(null);
		}
	}

	private ForumTreeItem createViewNode(NodeViewModel node) {
		ForumTreeItem viewNode = new ForumTreeItem(node);
		viewNode.setChildListener(change -> {	// wywolywanem, gdy w modelu dla tego wezla zmieni sie zawartosc kolekcji dzieci
			while (change.next()) {
				if (change.wasAdded()) {
					int i = change.getFrom();
					for (NodeViewModel child : change.getAddedSubList()) {
						addToTree(child, viewNode, i);	// uwzgledniamy nowy wezel modelu w widoku
						i++;
					}
				}

				if (change.wasRemoved()) {
					for (int i = change.getFrom(); i <= change.getTo(); ++i) {
						removeFromTree((ForumTreeItem) viewNode.getChildren().get(i)); // usuwamy wezel modelu z widoku
					}
				}
			}
		});

		return viewNode;
	}

	private void expandAll(TreeItem<NodeViewModel> item) {
		item.setExpanded(true);
		item.getChildren().forEach(this::expandAll);
	}

	private void onItemSelected(TreeItem<NodeViewModel> oldItem, TreeItem<NodeViewModel> newItem) {
		detailsController.setModel(newItem != null ? newItem.getValue() : null);
	}

}
