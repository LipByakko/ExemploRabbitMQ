package br.ufs.dcomp.ExemploRabbitMQ;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.rabbitmq.client.*;

import br.ufs.dcomp.ExemploRabbitMQ.MensagemProto.Conteudo;
import br.ufs.dcomp.ExemploRabbitMQ.MensagemProto.Mensagem;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.Base64;
//import java.util.TimeZone;


public class Chat{

    private static final String EXCHANGE_NAME = "chat_exchange";
    private static final String QUEUE_PREFIX = "user_queue_";
    private String username;
    private String currentRecipient = null;
    private Channel channel;

    public Chat(String username) throws Exception{
        this.username = username;

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("load-balancer-514a88fa1c6c5cde.elb.us-east-1.amazonaws.com"); // REMINDER: Need to change this everytime I start the Lab, cuz the RabbitMQ's IP reset
        factory.setUsername("admin");
        factory.setPassword("password");
        factory.setVirtualHost("/");
        Connection connection = factory.newConnection();
        channel = connection.createChannel();
        channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.DIRECT); // Declare exchange

        String queueName = QUEUE_PREFIX + username; // Declare queue for the user
        channel.queueDeclare(queueName, false, false, false, null);
        channel.queueBind(queueName, EXCHANGE_NAME, username); // Bind the queue to the exchange with the username as the routing key
        startListening(queueName);
    }

    private void startListening(String queueName) throws IOException{
        DeliverCallback deliverCallback = (consumerTag, delivery) ->{
            byte[] decoded = Base64.getDecoder().decode(delivery.getBody());
            handleMessage(decoded);
        };

        channel.basicConsume(queueName, true, deliverCallback, consumerTag ->{});
    }

    private void handleMessage(byte[] messageBody) {
        try {
            Mensagem mensagem = Mensagem.parseFrom(messageBody);
            
            if (mensagem.getConteudo().getTipo().compareTo("text/plain") == 0) {
                String output = String.format("(%s às %s) %s%s diz: %s",
                    mensagem.getData(), mensagem.getHora(), 
                    mensagem.getEmissor(), 
                    mensagem.getGrupo().compareTo("") != 0 ? "#" + mensagem.getGrupo() : "",
                    mensagem.getConteudo().getCorpo().toStringUtf8()
                );
                System.out.println("\n" + output);
                System.out.print(getPrompt());
            } else {
                Thread thread = new Thread(new Receivable("testes", mensagem)); 
                thread.start();
            }
    
            
        } catch (InvalidProtocolBufferException e) {
            System.err.println("Erro ao deserializar a mensagem: " + e.getMessage());
        }
    }

    public void startChat() throws IOException{
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        System.out.println(">> Welcome to the chat!");

        while(true){
            System.out.print(getPrompt());
            String input = br.readLine();

            if(input.startsWith("@")){
                currentRecipient = input;
            } else if (input.startsWith("!addGroup")) {
                String groupName = input.split(" ")[1];
                createGroup(groupName);
            } else if (input.startsWith("!addUser")) {
                String[] parts = input.split(" ");
                String user = parts[1];
                String groupName = parts[2];
                addUserToGroup(user, groupName);
            } else if (input.startsWith("#")) {
                currentRecipient = input;
            } else if (input.startsWith("!delFromGroup")) {
                String[] parts = input.split(" ");
                String user = parts[1];
                String groupName = parts[2];
                removeUserFromGroup(user, groupName);
            } else if (input.startsWith("!removeGroup")) {
                String groupName = input.split(" ")[1];
                removeGroup(groupName);
            } else if (input.startsWith("!upload")) {
                String[] parts = input.split(" ");
                String path = parts[1];
                sendFile(path);
            } else {
                sendMessage(input);
            }
        }
    }

    private void sendFile(String path) throws IOException {
        if (currentRecipient != null) {
            SimpleDateFormat sdfData = new SimpleDateFormat("dd/MM/yyyy");
            SimpleDateFormat sdfHora = new SimpleDateFormat("HH:mm:ss");
            String dataAtual = sdfData.format(new Date());
            String horaAtual = sdfHora.format(new Date());
            String group = currentRecipient.startsWith("#") ? currentRecipient.substring(1) : "";
            
            Path source = Paths.get(path);
            String tipoMime = Files.probeContentType(source);
            byte[] bytes = Files.readAllBytes(source);
            
            ByteString byteString = ByteString.copyFrom(bytes);
            
            Conteudo conteudo = Conteudo.newBuilder()
                .setTipo(tipoMime)
                .setNome(source.getFileName().toString())
                .setCorpo(byteString)
                .build();
        
            Mensagem mensagem = Mensagem.newBuilder()
                .setEmissor(username)
                .setData(dataAtual)
                .setHora(horaAtual)
                .setConteudo(conteudo)
                .setGrupo(group)
                .build();
        
            byte[] mensagemBytes = mensagem.toByteArray();
            byte[] encoded = Base64.getEncoder().encode(mensagemBytes);
            
            Thread thread = new Thread(new Sendable(encoded, currentRecipient, channel, group)); 
            thread.start();
        }
    }

    private void sendMessage(String conteudoTexto) throws IOException {
        if (currentRecipient != null) {
            SimpleDateFormat sdfData = new SimpleDateFormat("dd/MM/yyyy");
            SimpleDateFormat sdfHora = new SimpleDateFormat("HH:mm:ss");
            String dataAtual = sdfData.format(new Date());
            String horaAtual = sdfHora.format(new Date());
            String group = currentRecipient.startsWith("#") ? currentRecipient.substring(1) : "";
            
            Conteudo conteudo = Conteudo.newBuilder()
                .setTipo("text/plain")
                .setCorpo(ByteString.copyFromUtf8(conteudoTexto))
                .build();
        
            Mensagem mensagem = Mensagem.newBuilder()
                .setEmissor(username)
                .setData(dataAtual)
                .setHora(horaAtual)
                .setConteudo(conteudo)
                .setGrupo(group)
                .build();
        
            byte[] mensagemBytes = mensagem.toByteArray();
            byte[] encoded = Base64.getEncoder().encode(mensagemBytes);
            
            // If receiver is a group send message to the group's exchange.
            if (currentRecipient.startsWith("#")){
                channel.basicPublish(group, "", null, encoded);
            } else {
                channel.basicPublish(EXCHANGE_NAME, currentRecipient.substring(1), null, encoded);
            }
        }
    }

    private void createGroup(String groupName) throws IOException {
        channel.exchangeDeclare(groupName, BuiltinExchangeType.FANOUT);
        addUserToGroup(username, groupName);
    }
    
    private void removeUserFromGroup(String user, String groupName) throws IOException {
        String queueName = QUEUE_PREFIX + user;
        channel.queueUnbind(queueName, groupName, "");
    }
    
    private void removeGroup(String groupName) throws IOException {
        channel.exchangeDelete(groupName);
    }

    private String getPrompt(){
        if(currentRecipient != null){
            return currentRecipient + ">> ";
        } else{
            return ">> ";
        }
    }
    
    private void addUserToGroup(String user, String groupName) throws IOException {
        String queueName = QUEUE_PREFIX + user;
        channel.queueBind(queueName, groupName, "");
    }


    public static void main(String[] args) throws Exception{
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        System.out.print("User: ");
        String username = br.readLine();
        
        Chat chat = new Chat(username);
        chat.startChat();
    }
}
