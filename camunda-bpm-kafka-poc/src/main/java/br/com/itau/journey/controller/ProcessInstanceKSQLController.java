package br.com.itau.journey.controller;

import br.com.itau.journey.domain.KafkaExternalTasks;
import br.com.itau.journey.dto.RequestStartDTO;
import br.com.itau.journey.rocksdb.RocksDBKeyValueService;
import br.com.itau.journey.rocksdb.kv.exception.FindFailedException;
import br.com.itau.journey.rocksdb.mapper.exception.SerDeException;
import br.com.itau.journey.service.ProcessInstanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Optional;

@RestController
@RequestMapping("instance/ksql")
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Slf4j
public class ProcessInstanceKSQLController {

    private final ProcessInstanceService processInstanceService;
    private final RocksDBKeyValueService rocksDBKeyValueService;

    @PostMapping("/start")
    @Consumes("application/json")
    @Produces("application/json")
    public ResponseEntity<String> start(@RequestBody RequestStartDTO requestStart) throws InterruptedException, URISyntaxException {
        final String processInstanceId = processInstanceService.startProcessInstance(requestStart.getBpmnInstance());
        log.info(":: 1 - Created instance with id {}", processInstanceId);

        String ksql = "select * from STEPS_PROCESS_STREAM " +
                " where processInstanceId = '" + processInstanceId + "' " +
                " and (type = 'UserTask' or type = 'EndEvent') " +
                " limit 1;";

        String result = waitingProcessEnd(ksql);

        log.info(":: 2 - Result of Streams {}", result);

        return ResponseEntity.ok(result);
    }

    @PostMapping("/{processInstanceId}/complete/{taskId}")
    public ResponseEntity<String> complete(@PathVariable String processInstanceId, @PathVariable String taskId) throws URISyntaxException, InterruptedException {
        processInstanceService.completeTask(taskId);
        log.info(":: 1 - Complete task id {}", taskId);

        String ksql = "select * from STEPS_PROCESS_STREAM " +
                " where processInstanceId = '" + processInstanceId + "' " +
                " and type = 'EndEvent' " +
                " limit 1;";

        String result = waitingProcessEnd(ksql);

        log.info(":: 2 - Result of Streams {}", result);

        return ResponseEntity.ok(result);
    }

    private String waitingProcessEnd(String ksql) throws InterruptedException, URISyntaxException {
        boolean x = true;
        ResponseEntity<String> result = null;
        while (x) {
            result = processInstanceService.getProcessInstance(ksql);
            x = result.getBody() == null || result.getBody().isEmpty();
        }
        return result.getBody();
    }

    @GetMapping("/get/{processInstanceId}")
    @Produces("application/json")
    public ResponseEntity<String> get(@PathVariable String processInstanceId) throws URISyntaxException, FindFailedException, SerDeException {
        Optional<String> byKey = rocksDBKeyValueService.findByKey(processInstanceId);
        return ResponseEntity.ok(byKey.get());
    }

    @GetMapping("/get")
    @Produces("application/json")
    public ResponseEntity<String> get() throws URISyntaxException, FindFailedException, SerDeException {
        Collection<String> values = rocksDBKeyValueService.findAll();
        return ResponseEntity.ok(values.toString());
    }
}

