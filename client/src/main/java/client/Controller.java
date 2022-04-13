package client;

import constants.Command;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.ResourceBundle;

public class Controller implements Initializable {
    @FXML
    public TextField textField;
    @FXML
    public TextArea textArea;
    @FXML
    public TextField loginField;
    @FXML
    public PasswordField passwordField;
    @FXML
    public HBox authPanel;
    @FXML
    public HBox msgPanel;
    @FXML
    public ListView<String> clientList;

    private Socket socket;
    private static final int PORT = 8189;
    private static final String ADDRESS = "localhost";

    private DataInputStream in;
    private DataOutputStream out;

    private boolean authenticated;
    private String nickname;
    private Stage stage;
    private Stage regStage;
    private RegController regController;

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
        authPanel.setVisible(!authenticated);
        authPanel.setManaged(!authenticated);
        msgPanel.setVisible(authenticated);
        msgPanel.setManaged(authenticated);
        clientList.setVisible(authenticated);
        clientList.setManaged(authenticated);

        if (!authenticated) {
            nickname = "";
        }

        textArea.clear();
        setTitle(nickname);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        Platform.runLater(() -> {
            stage = (Stage) textField.getScene().getWindow();
            stage.setOnCloseRequest(event -> {
                System.out.println("bye");
                if (socket != null && !socket.isClosed()) {
                    try {
                        out.writeUTF(Command.END);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        });

        setAuthenticated(false);
    }

    private void connect() {
        try {
            socket = new Socket(ADDRESS, PORT);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());


            new Thread(() -> {
                try {
                    //цикл аутентификации
                    while (true) {
                        String str = in.readUTF();

                        if (str.startsWith("/")) {
                            if (str.equals(Command.END)) {
                                break;
                            }
                            if (str.startsWith(Command.AUTH_OK)) {
                                nickname = str.split(" ")[1];
                                setAuthenticated(true);
                                showCommonHistory();
                                break;
                            }
                            if (str.equals(Command.REG_OK) || str.equals(Command.REG_NO)) {
                                regController.result(str);
                            }
                        } else {
                            textArea.appendText(str + "\n");
                        }
                    }
                    //цикл работы
                    while (authenticated) {

                        String str = in.readUTF();

                        //запись истории чата для конкретного клиента
                        String personalHistory = "history_" + loginField.getText().trim() + ".txt";
                        FileOutputStream personalWriter = new FileOutputStream(personalHistory, true);

                        //запись общей истории чата
                        if (str.startsWith("[ " + loginField.getText().trim() + " ]")) {
                            String[] token = str.split("]" + " to " + "\\[");
                            if (token.length == 1) {
                                writeCommonHistory(str);
                            }
                        }

                        if (str.startsWith("/")) {

                            if (str.equals(Command.END)) {
                                personalWriter.close();
                                break;
                            }

                            if (str.startsWith(Command.CLIENT_LIST)) {
                                String[] token = str.split(" ");

                                Platform.runLater(() -> {
                                    clientList.getItems().clear();
                                    for (int i = 1; i < token.length; i++) {
                                        clientList.getItems().add(token[i]);
                                    }
                                });
                            }

                            if (str.startsWith(Command.CHANGE_NICK_OK)) {
                                String[] token = str.split(" ");
                                nickname = token[1].trim();
                                setTitle(nickname);
                                textArea.appendText("Никнейм успешно изменен\n");

                            } else if (str.startsWith(Command.CHANGE_NICK_NO)) {
                                textArea.appendText("Изменение никнейма не удалось\n");
                            }
                        } else {
                            personalWriter.write(str.getBytes(StandardCharsets.UTF_8));
                            personalWriter.write(13);
                            personalWriter.close();

                            textArea.appendText(str + "\n");
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    setAuthenticated(false);
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void sendMsg(ActionEvent actionEvent) {
        try {
            out.writeUTF(textField.getText());
            textField.clear();
            textField.requestFocus();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void writeCommonHistory(String str) {
        try {
            String sharedHistory = "shared_history.txt";
            FileOutputStream sharedWriter = new FileOutputStream(sharedHistory, true);

            sharedWriter.write(str.getBytes(StandardCharsets.UTF_8));
            sharedWriter.write(13);
            sharedWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void showCommonHistory() {
        if (Files.exists(Paths.get("shared_history.txt"))) {
            try {
                ArrayList<String> list = new ArrayList<>(Files.readAllLines(Paths.get("shared_history.txt")));
                if (list.size() < 100) {
                    for (int i = 0; i < list.size(); i++) {
                        textArea.appendText(list.get(i) + "\n");
                    }
                } else {
                    for (int i = 0; i < 100; i++) {
                        textArea.appendText(list.get(i) + "\n");
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void tryToAuth(ActionEvent actionEvent) {
        if (socket == null || socket.isClosed()) {
            connect();
        }

        String msg = String.format("/auth %s %s", loginField.getText().trim(), passwordField.getText().trim());
        passwordField.clear();

        try {
            out.writeUTF(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setTitle(String nickname) {
        String title;
        if (nickname.equals("")) {
            title = "Chatty";
        } else {
            title = String.format("Chatty [ %s ]", nickname);
        }
        Platform.runLater(() -> {
            stage.setTitle(title);
        });
    }

    public void clientListMouseAction(MouseEvent mouseEvent) {
        String receiver = clientList.getSelectionModel().getSelectedItem();
        textField.setText(String.format("/w %s ", receiver));
    }

    private void createRegStage() {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/reg.fxml"));
            Parent root = fxmlLoader.load();

            regStage = new Stage();

            regStage.setTitle("Chatty registration");
            regStage.setScene(new Scene(root, 600, 500));

            regController = fxmlLoader.getController();
            regController.setController(this);

            regStage.initStyle(StageStyle.UTILITY);
            regStage.initModality(Modality.APPLICATION_MODAL);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void tryToReg(ActionEvent actionEvent) {
        if (regStage == null) {
            createRegStage();
        }
        regStage.show();
    }

    public void registration(String login, String password, String nickname) {
        String msg = String.format("/reg %s %s %s", login, password, nickname);

        if (socket == null || socket.isClosed()) {
            connect();
        }

        try {
            out.writeUTF(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
