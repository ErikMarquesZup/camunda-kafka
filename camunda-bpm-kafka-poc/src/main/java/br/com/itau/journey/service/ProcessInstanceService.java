package br.com.itau.journey.service;

import br.com.itau.journey.constant.TypeComponent;
import br.com.itau.journey.domain.KafkaExternalTask;
import br.com.itau.journey.dto.RequestStartDTO;
import br.com.itau.journey.rocksdb.RocksDBKeyValueService;
import br.com.itau.journey.rocksdb.kv.exception.FindFailedException;
import br.com.itau.journey.rocksdb.mapper.exception.SerDeException;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.runtime.ActivityInstance;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Slf4j
@Component
public class ProcessInstanceService {

    @Autowired
    private RuntimeService runtimeService;
    @Autowired
    private TaskService taskService;
    @Autowired
    private KSQLInstanceService ksqlInstanceService;
    @Autowired
    private ProducerService producerService;
    @Autowired
    private RocksDBKeyValueService rocksDBKeyValueService;

    public String completeTask(String taskId) {
        Optional<Task> task = Optional.of(taskService.createTaskQuery().taskId(taskId).singleResult());
        if (!task.isPresent()) {
            throw new TaskRejectedException("Task id not Found!");
        }
        Message<KafkaExternalTask> message = MessageBuilder
                .withPayload(KafkaExternalTask.builder().type(TypeComponent.USER_TASK.getEvent())
                        .internalUserTask(Boolean.FALSE)
                        .taskId(taskId)
                        .processInstanceId(task.get().getProcessInstanceId())
                        .build())
                .setHeader(KafkaHeaders.TOPIC, "user-tasks-process")
                .build();
        producerService.sendToKafka(message);
        return task.get().getProcessInstanceId();
    }

    public String start(RequestStartDTO request) {
        String uuid = UUID.randomUUID().toString();
        Message<KafkaExternalTask> message = MessageBuilder
                .withPayload(KafkaExternalTask.builder()
                            .type(TypeComponent.START_EVENT.getEvent())
                            .cpf(request.getCpf())
                            .uuid(uuid)
                            .internalUserTask(Boolean.TRUE)
                            .bpmnInstance(request.getBpmnInstance()).uuid(uuid).build())
                .setHeader(KafkaHeaders.TOPIC, "start-process")
                .build();
        producerService.sendToKafka(message);
        return uuid;
    }

    public Optional<String> getProcessingInstance(String cpf) throws FindFailedException, SerDeException {
        Optional<String> values = rocksDBKeyValueService.findByKey(cpf);
        if (values.isPresent()) {
            return values;
        }
        return Optional.empty();
    }

    public String startProcessInstance(String sample) {
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(sample);
        String processInstanceId = processInstance.getProcessInstanceId();
        log.info("started instance: {}", processInstanceId);
        return processInstanceId;
    }

    public ResponseEntity<String> getProcessInstance(String ksql) throws URISyntaxException {
        return ksqlInstanceService.getProcessInstance(ksql);
    }

    public KafkaExternalTask startReply(RequestStartDTO request) throws InterruptedException, ExecutionException, IOException {
        String uuid = UUID.randomUUID().toString();
        Message<KafkaExternalTask> message = MessageBuilder
                .withPayload(KafkaExternalTask.builder()
                        .type(TypeComponent.START_EVENT.getEvent())
                        .cpf(request.getCpf())
                        .uuid(uuid)
                        .internalUserTask(Boolean.TRUE)
                        .id("12345")
                        .bpmnInstance(request.getBpmnInstance()).uuid(uuid).build())
                .setHeader(KafkaHeaders.TOPIC, "start-reply-process")
                .setHeader(KafkaHeaders.REPLY_TOPIC, "reply-request-process")
                .build();
        return producerService.sendToKafkaReply(message);
    }
}