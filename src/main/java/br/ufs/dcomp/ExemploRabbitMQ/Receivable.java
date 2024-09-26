package br.ufs.dcomp.ExemploRabbitMQ;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;

import br.ufs.dcomp.ExemploRabbitMQ.MensagemProto.Mensagem;

public class Receivable implements Runnable{
    private String prompt;
    private Mensagem mensagem;
    
    
    public Receivable(String prompt, Mensagem mensagem) {
        this.prompt = prompt;
        this.mensagem = mensagem;
    }


    @Override
    public void run() {
        try {
            Path download = Paths.get("downloads");
            if(Files.notExists(download)){
                Files.createDirectory(download);
            }
            Path arquivo = Paths.get("downloads", mensagem.getConteudo().getNome());
            Files.write(arquivo, mensagem.getConteudo().getCorpo().toByteArray());
            
            String output = String.format("(%s às %s) Arquivo \"%s\" recebido de %s%s",
                mensagem.getData(), mensagem.getHora(),
                mensagem.getConteudo().getNome(),
                mensagem.getEmissor(), 
                mensagem.getGrupo().compareTo("") != 0 ? "#" + mensagem.getGrupo() : ""
            );
            System.out.println("\n" + output);
            System.out.print(prompt);
        } catch (IOException e) {
            System.out.println("Erro ao receber arquivo!");
        }
    }
    
}
