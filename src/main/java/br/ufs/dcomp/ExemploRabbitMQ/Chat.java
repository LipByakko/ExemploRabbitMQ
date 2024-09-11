//package br.ufs.dcomp.ExemploRabbitMQ; //idk if I'm gonna need this eventually, need to ask Tarcisio about it
import com.rabbitmq.client.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import your.package.generated.MensagemOuterClass.Mensagem;
import com.google.protobuf;


public class Chat{

    private static final String EXCHANGE_NAME = "chat_exchange";
    private static final String QUEUE_PREFIX = "user_queue_";
    private String username;
    private String currentRecipient = null;
    private Channel channel;

    public Chat(String username) throws Exception{
        this.username = username;

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("ec2-3-91-73-187.compute-1.amazonaws.com"); // REMINDER: Need to change this everytime I start the Lab, cuz the RabbitMQ's IP reset
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
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            byte[] mensagemBytes = message.getBytes();
            handleMessage(mensagemBytes);
        };

        channel.basicConsume(queueName, true, deliverCallback, consumerTag ->{});
    }

    private void handleMessage(byte[] messageBody) {
        try {
            Mensagem mensagem = Mensagem.parseFrom(messageBody);
    
            String output = String.format("(%s às %s) %s%s diz: %s",
                mensagem.getData(), mensagem.getHora(), 
                mensagem.getEmissor(), 
                mensagem.hasGrupo() ? "#" + mensagem.getGrupo() : "",
                mensagem.getConteudo().getCorpo().toStringUtf8()
            );
    
            System.out.println(output);
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
                currentRecipient = input.substring(1);
                
            } else if (input.startsWith("!addGroup")) {
                String groupName = input.split(" ")[1];
                createGroup(groupName);
            } else if (input.startsWith("!addUser")) {
                String[] parts = input.split(" ");
                String user = parts[1];
                String groupName = parts[2];
                addUserToGroup(user, groupName);
            } else if (input.startsWith("#")) {
                currentRecipient = input.substring(1);
                System.out.println("Grupo selecionado: " + currentRecipient);
            } else if (input.startsWith("!delFromGroup")) {
                String[] parts = input.split(" ");
                String user = parts[1];
                String groupName = parts[2];
                removeUserFromGroup(user, groupName);
            } else if (input.startsWith("!removeGroup")) {
                String groupName = input.split(" ")[1];
                removeGroup(groupName);
            } else {
                sendMessage(input);
            }
        }
    }

    private void sendMessage(String conteudoTexto) throws IOException {
        SimpleDateFormat sdfData = new SimpleDateFormat("dd/MM/yyyy");
        SimpleDateFormat sdfHora = new SimpleDateFormat("HH:mm:ss");
        String dataAtual = sdfData.format(new Date());
        String horaAtual = sdfHora.format(new Date());
    
        Mensagem.Conteudo conteudo = Mensagem.Conteudo.newBuilder()
            .setTipo("text/plain")
            .setCorpo(ByteString.copyFromUtf8(conteudoTexto))
            .build();
    
        Mensagem mensagem = Mensagem.newBuilder()
            .setEmissor(username)
            .setData(dataAtual)
            .setHora(horaAtual)
            .setConteudo(conteudo)
            .build();
    
        byte[] mensagemBytes = mensagem.toByteArray();
        channel.basicPublish(EXCHANGE_NAME, routingKey, null, mensagemBytes);
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
            return "@" + currentRecipient + ">> ";
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
        System.out.print("Enter your username: ");
        String username = br.readLine();
        
        Chat chat = new Chat(username);
        chat.startChat();
    }
}
