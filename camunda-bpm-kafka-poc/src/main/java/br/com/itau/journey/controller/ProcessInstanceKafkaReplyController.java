package br.com.itau.journey.controller;

import br.com.itau.journey.domain.KafkaExternalTask;
import br.com.itau.journey.dto.RequestStartDTO;
import br.com.itau.journey.rocksdb.RocksDBKeyValueService;
import br.com.itau.journey.service.ProcessInstanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("instance/reactive")
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Slf4j
public class ProcessInstanceKafkaReplyController {


    private final ProcessInstanceService processInstanceService;
    private final RocksDBKeyValueService rocksDBKeyValueService;

    @PostMapping("/start")
    @Consumes("application/json")
    @Produces("application/json")
    public ResponseEntity<KafkaExternalTask> start(@RequestBody RequestStartDTO requestStart) throws IOException, ExecutionException, InterruptedException {
        KafkaExternalTask result = processInstanceService.startReply(requestStart);
        log.info(":: 1 - Created instance with id execution {}", result.getProcessInstanceId());
        log.info(":: 2 - Result of Streams {}", result);
        return ResponseEntity.ok(result);
    }
}

