package br.com.itau.journey.service;

import br.com.itau.journey.domain.KafkaExternalTask;
import br.com.itau.journey.domain.KafkaExternalTasks;
import br.com.itau.journey.rocksdb.RocksDBKeyValueService;
import br.com.itau.journey.rocksdb.kv.exception.SaveFailedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;

@Service
@Slf4j
public class ConsumerService {

    private ObjectMapper objectMapper;
    private RocksDBKeyValueService rocksDBKeyValueService;
    private ProcessInstanceService processInstanceService;
    private TaskService taskService;
    private ProducerService producerService;
    private final String TOPIC_START_EVENT = "start-process";
    private final String TOPIC_START_REPLY_EVENT = "start-reply-process";
    private final String TOPIC_STEPS_EVENT = "steps-process";
    private final String TOPIC_COMPLETE_TASK = "complete-process";
    private final String TOPIC_UPDATE_PROPOSAL_EVENT = "update-proposal-process";
    private final String TOPIC_USER_TASK_EVENT = "user-tasks-process";
    private final String TOPIC_USER_TASK_REPLY_EVENT = "user-tasks-reply-process";
    private final String TOPIC_FRAUD_EVENT = "fraud-process";

    @Autowired
    public ConsumerService(ObjectMapper objectMapper, RocksDBKeyValueService rocksDBKeyValueService, ProcessInstanceService processInstanceService, TaskService taskService, ProducerService producerService) {
        this.objectMapper = objectMapper;
        this.rocksDBKeyValueService = rocksDBKeyValueService;
        this.processInstanceService = processInstanceService;
        this.taskService = taskService;
        this.producerService = producerService;
    }

    @KafkaListener(
            id = "startEventProcessor",
            topics = TOPIC_START_EVENT,
            containerFactory = "kafkaListenerContainerFactory")
    public void startEventProcessor(String message) throws IOException, SaveFailedException, InterruptedException {
        KafkaExternalTask externalTask = this.objectMapper.readValue(message, KafkaExternalTask.class);
        String processInstanceId = processInstanceService.startProcessInstance(externalTask.getBpmnInstance());
        externalTask.setProcessInstanceId(processInstanceId);
        log.info(":: Listener Start ProcessInstanceId {} - Process: {}",processInstanceId,  message);
        rocksDBKeyValueService.save(externalTask.getUuid(), externalTask.getProcessInstanceId());
        rocksDBKeyValueService.save(externalTask.getCpf(), externalTask.getProcessInstanceId());
    }


    @KafkaListener(
            id = "startReplyEventProcessor",
            topics = TOPIC_START_REPLY_EVENT,
            containerFactory = "kafkaListenerContainerFactory")
    @SendTo
    public KafkaExternalTask startEventReplyProcessor(String message) throws IOException {
        KafkaExternalTask externalTask = this.objectMapper.readValue(message, KafkaExternalTask.class);
        String processInstanceId = processInstanceService.startProcessInstance(externalTask.getBpmnInstance());
        externalTask.setProcessInstanceId(processInstanceId);
        log.info(":: Listener Start ProcessInstanceId {} - Process: {}",processInstanceId,  message);
        return externalTask;
    }

    @KafkaListener(
            id = "stepsEventProcessor",
            topics = TOPIC_STEPS_EVENT,
            containerFactory = "kafkaListenerContainerFactory")
    public void stepsEventProcessor(String message) throws IOException, SaveFailedException {
        KafkaExternalTask externalTask = this.objectMapper.readValue(message, KafkaExternalTask.class);
        log.info(":: Listener Step Process: {}",  message);
        KafkaExternalTasks kafkaExternalTasks = KafkaExternalTasks.builder().kafkaExternalTasks(Collections.singletonList(externalTask)).build();
        rocksDBKeyValueService.save(externalTask.getProcessInstanceId(), this.objectMapper.writeValueAsString(kafkaExternalTasks));
    }

    @KafkaListener(
            id = "updateProposalEventProcessor",
            topics = TOPIC_UPDATE_PROPOSAL_EVENT,
            containerFactory = "kafkaListenerContainerFactory")
    public void updateProposalEventProcessor(String message) throws IOException, InterruptedException {
        Thread.sleep(3000);
        KafkaExternalTask externalTask = this.objectMapper.readValue(message, KafkaExternalTask.class);
        log.info(":: Listener Update Proposal Process: {}",  message);
        // update proposal process
        this.sendKafka(externalTask, TOPIC_COMPLETE_TASK);
    }

    @KafkaListener(
            id = "userTaskEventProcessor",
            topics = TOPIC_USER_TASK_EVENT,
            containerFactory = "kafkaListenerContainerFactory")
    public void userTaskEventProcessor(String message) throws IOException, SaveFailedException {
        KafkaExternalTask externalTask = this.objectMapper.readValue(message, KafkaExternalTask.class);
        externalTask.setInternalUserTask(Boolean.FALSE);
        log.info(":: Listener User Task Process: {}",  message);
        // atualizacao dados cadastrais
        this.sendKafka(externalTask, TOPIC_COMPLETE_TASK);
        rocksDBKeyValueService.setCompleteTask(externalTask.getTaskId(), externalTask.getProcessInstanceId());
    }

    @KafkaListener(
            id = "userTaskReplyEventProcessor",
            topics = TOPIC_USER_TASK_REPLY_EVENT,
            containerFactory = "kafkaListenerContainerFactory")
    @SendTo("reply-request-process")
    public KafkaExternalTask userTaskReplyEventProcessor(String message) throws IOException, SaveFailedException {
        KafkaExternalTask externalTask = this.objectMapper.readValue(message, KafkaExternalTask.class);
        log.info(":: Listener User Task Process: {}",  message);
        return externalTask;
    }

    @KafkaListener(
            id = "fraudEventProcessor",
            topics = TOPIC_FRAUD_EVENT,
            containerFactory = "kafkaListenerContainerFactory")
    public void fraudEventProcessor(String message) throws IOException, InterruptedException {
        KafkaExternalTask externalTask = this.objectMapper.readValue(message, KafkaExternalTask.class);
        log.info(":: Listener Fraud Process: {}",  message);
        this.sendKafka(externalTask, TOPIC_COMPLETE_TASK);
    }

    @KafkaListener(
            id = "completeTaskProcessor",
            topics = TOPIC_COMPLETE_TASK,
            containerFactory = "kafkaListenerContainerFactory")
    public void listenComplete(String message) throws IOException, InterruptedException {
        Thread.sleep(3000);
        KafkaExternalTask externalTask = this.objectMapper.readValue(message, KafkaExternalTask.class);
        log.info(":: Listener Complete Process: {}",  message);
        this.completeTask(externalTask.getTaskId());
    }

    private void completeTask(String taskId) {
        log.info(":: Completing task: {}", taskId);
        taskService.complete(taskId);
        log.info(":: Completed task: {}", taskId);
    }

    private void sendKafka(KafkaExternalTask kafkaExternalTask, String topic) {
        Message<KafkaExternalTask> message = MessageBuilder
                .withPayload(kafkaExternalTask)
                .setHeader(KafkaHeaders.TOPIC, topic)
                .build();
        producerService.sendToKafka(message);
    }

}
