package br.ufs.dcomp.ExemploRabbitMQ;

import java.io.IOException;

import com.rabbitmq.client.Channel;

public class Sendable implements Runnable{
    private byte[] data;
    private String currentRecipient;
    private Channel channel;
    private String filePath;
    private static final String EXCHANGE_NAME = "chat_exchange";

    public Sendable(byte[] data, String currentRecipient, Channel channel, String filePath) {
        this.data = data;
        this.currentRecipient = currentRecipient;
        this.channel = channel;
        this.filePath = filePath;
    }

    @Override
    public void run() {
        try {
            if (currentRecipient.startsWith("#")){
                channel.basicPublish(currentRecipient.substring(1), "", null, data);
            } else {
                channel.basicPublish(EXCHANGE_NAME, currentRecipient.substring(1), null, data);
            }
            
            System.out.println("\nArquivo " + filePath + " foi enviado para " + currentRecipient + "!");
            System.out.println(currentRecipient + ">> ");
            
        } catch (IOException e) {
            System.err.println("\nErro ao enviar arquivo: " + e.getMessage());
            System.out.println(currentRecipient + ">> ");
        };
    }
}
