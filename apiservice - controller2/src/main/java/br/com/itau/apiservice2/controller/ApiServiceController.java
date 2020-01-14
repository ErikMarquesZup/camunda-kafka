package br.com.itau.apiservice2.controller;

import java.util.Random;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api-service2")
public class ApiServiceController {

    @GetMapping
    public ResponseEntity<?> exeService() throws InterruptedException {

        Integer valorExecution = new Random().nextInt(6);
        if (valorExecution > 0){

            Thread.sleep((valorExecution * 1000));

            return new ResponseEntity("Executando a API service 2", HttpStatus.OK);
        }else
            return new ResponseEntity("Ocorreu um erro na execução do serviço API service 2", HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
