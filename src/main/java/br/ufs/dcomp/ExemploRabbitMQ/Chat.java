//package br.ufs.dcomp.ExemploRabbitMQ; //idk if I'm gonna need this eventually, need to ask Tarcisio about it
import com.rabbitmq.client.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

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
            handleMessage(message);
        };

        channel.basicConsume(queueName, true, deliverCallback, consumerTag ->{});
    }

    private void handleMessage(String message){
        SimpleDateFormat sdf = new SimpleDateFormat("(dd/MM/yyyy, HH:mm:ss)");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT-3")); // Changing timezone to BR's
        String timestamp = sdf.format(new Date());

        System.out.println(timestamp + " " + message);
        if(currentRecipient != null){
            System.out.print("@" + currentRecipient + ">> ");
        } else{
            System.out.print(">> ");
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
            } else{
                sendMessage(input);
            }
        }
    }

    private void sendMessage(String message) throws IOException{
        String routingKey = (currentRecipient != null) ? currentRecipient : "";
        String formattedMessage = username + " diz: " + message;
        channel.basicPublish(EXCHANGE_NAME, routingKey, null, formattedMessage.getBytes(StandardCharsets.UTF_8));
    }

    private String getPrompt(){
        if(currentRecipient != null){
            return "@" + currentRecipient + ">> ";
        } else{
            return ">> ";
        }
    }

    public static void main(String[] args) throws Exception{
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        System.out.print("Enter your username: ");
        String username = br.readLine();
        
        Chat chat = new Chat(username);
        chat.startChat();
    }
}
