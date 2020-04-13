package br.com.itau.journey.service;

import br.com.itau.journey.domain.KafkaExternalTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.requestreply.ReplyingKafkaTemplate;
import org.springframework.kafka.requestreply.RequestReplyFuture;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.Message;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

@Service
@Slf4j
public class ProducerService {

    private ReplyingKafkaTemplate<String, KafkaExternalTask, String> template;
    private ObjectMapper objectMapper;

    @Autowired
    public ProducerService(ReplyingKafkaTemplate<String, KafkaExternalTask, String> template, ObjectMapper objectMapper) {
        this.template = template;
        this.objectMapper = objectMapper;
    }

    @Async
    public void sendToKafka(Message<KafkaExternalTask> kafkaExternalTaskMessage) {
        ListenableFuture<SendResult<String, KafkaExternalTask>> future = template.send(kafkaExternalTaskMessage);
        future.addCallback(new ListenableFutureCallback<SendResult<String, KafkaExternalTask>>() {

            @Override
            public void onSuccess(SendResult<String, KafkaExternalTask> result) {
                log.info("Success :: " + ToStringBuilder.reflectionToString(result.toString()));
            }

            @Override
            public void onFailure(Throwable exception) {
                log.error(exception.getMessage());
            }
        });
    }

    public KafkaExternalTask sendToKafkaReply(Message<KafkaExternalTask> kafkaExternalTaskMessage) throws ExecutionException, InterruptedException, IOException {
        String requestTopic = kafkaExternalTaskMessage.getHeaders().get(KafkaHeaders.TOPIC).toString();
        String requestReplyTopic = kafkaExternalTaskMessage.getHeaders().get(KafkaHeaders.REPLY_TOPIC).toString();

        // create producer record
        KafkaExternalTask payload = kafkaExternalTaskMessage.getPayload();
        ProducerRecord<String, KafkaExternalTask> record = new ProducerRecord<>(requestTopic, payload.getId(), payload);

        // set reply topic in header
        record.headers().add(new RecordHeader(KafkaHeaders.REPLY_TOPIC, requestReplyTopic.getBytes()));

        record.headers().forEach(header ->    log.info(":: PRODUCER HEADERS :: " + header.key() + ":" + header.value().toString()));

        log.info(":: PRODUCER KEY :: " + record.key());

        // post in kafka topic
        RequestReplyFuture<String, KafkaExternalTask, String> sendAndReceive = template.sendAndReceive(record);

        // confirm if producer produced successfully
        SendResult<String, KafkaExternalTask> sendResult = sendAndReceive.getSendFuture().get();

        //print all headers
        sendResult.getProducerRecord().headers().forEach(header ->    log.info(":: CONSUMER HEADERS :: " + header.key() + ":" + header.value().toString()));

        // get consumer record
        ConsumerRecord<String, String> consumerRecord = sendAndReceive.get();

        // return consumer value
        KafkaExternalTask externalTask = objectMapper.readValue(consumerRecord.value(), KafkaExternalTask.class);
        return externalTask;
    }
}


