package server;

import constants.Command;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.logging.*;

public class ClientHandler {
    private static final Logger logger = Logger.getLogger(ClientHandler.class.getName());

    private Server server;
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;

    private boolean authenticated;
    private String nickname;
    private String login;

    public ClientHandler(Server server, Socket socket) {
        try {
            this.server = server;
            this.socket = socket;

            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            LogManager manager = LogManager.getLogManager();
            manager.readConfiguration(new FileInputStream("logging.properties"));

            new Thread(() -> {
                try {
                    socket.setSoTimeout(120000);
                    //цикл аутентификации
                    while (true) {
                        String str = in.readUTF();

                        if (str.startsWith("/")) {
                            if (str.equals(Command.END)) {
                                sendMsg(Command.END);
                                logger.info("Client " + getNickname() + " close app");
                                break;
                            }

                            if (str.startsWith(Command.AUTH)) {
                                String[] token = str.split(" ", 3);
                                if (token.length < 3) {
                                    continue;
                                }
                                String newNick = server.getAuthService()
                                        .getNicknameByLoginAndPassword(token[1], token[2]);
                                login = token[1];
                                if (newNick != null) {
                                    if (!server.isLoginAuthenticated(login)) {
                                        nickname = newNick;
                                        sendMsg(Command.AUTH_OK + nickname);
                                        authenticated = true;
                                        server.subscribe(this);
                                        socket.setSoTimeout(0);
                                        logger.info("Client " + getNickname() + " is authenticated");
                                        break;
                                    } else {
                                        sendMsg("Учетная запись уже используется");
                                    }
                                } else {
                                    sendMsg("Логин / пароль не верны");
                                }
                            }

                            if (str.startsWith(Command.REG)) {
                                String[] token = str.split(" ");
                                if (token.length < 4) {
                                    continue;
                                }
                                if (server.getAuthService().registration(token[1], token[2], token[3])) {
                                    sendMsg(Command.REG_OK);
                                    logger.info("Client " + getNickname() + " complete registration");
                                } else {
                                    sendMsg(Command.REG_NO);
                                    logger.info("Client " + getNickname() + " not complete registration");
                                }
                            }
                        }
                    }
                    //цикл работы
                    while (authenticated) {

                        String str = in.readUTF();

                        if (str.startsWith("/")) {

                            if (str.equals(Command.END)) {
                                sendMsg(Command.END);
                                break;
                            }

                            if (str.startsWith(Command.W)) {
                                String[] token = str.split(" ", 3);
                                if (token.length < 3) {
                                    continue;
                                }
                                server.privateMsg(this, token[1], token[2]);
                                logger.fine("Client " + getNickname() + " send private message to " + token[1].trim());
                            }

                            if (str.startsWith(Command.CHANGE_NICK)) {
                                String[] token = str.split(" ");
                                if (token.length < 3) {
                                    continue;
                                }

                                if (server.getAuthService().changeNickname(token[1], token[2])) {
                                    sendMsg(Command.CHANGE_NICK_OK + " " + token[2]);
                                    nickname = token[2];
                                    server.broadcastClientList();
                                    logger.info("Client " + token[1] + " change nickname to " + token[2]);
                                } else {
                                    sendMsg(Command.CHANGE_NICK_NO);
                                }
                            }

                        } else {
                            server.broadcastMsg(this, str);
                            logger.fine("Client " + this.getNickname() + " send broadcast message");
                        }
                    }
                } catch (SocketTimeoutException ex) {
                    sendMsg(Command.END);
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    server.unsubscribe(this);
                    logger.info("Client " + getNickname() + " disconnected");
                    try {
                        DataBaseAuthService.disconnect();
                        logger.severe("Database connection for client " + getNickname() + " is shutdown");
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

            }).start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendMsg(String msg) {
        try {
            out.writeUTF(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getNickname() {
        return nickname;
    }

    public String getLogin() {
        return login;
    }
}
