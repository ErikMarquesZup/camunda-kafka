package br.com.itau.journey.service;

import br.com.itau.journey.domain.KafkaExternalTask;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.requestreply.ReplyingKafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.Message;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

@Service
@Slf4j
public class ProducerService {

    private ReplyingKafkaTemplate<String, Message<KafkaExternalTask>, String> template;

    @Autowired
    public ProducerService(ReplyingKafkaTemplate<String, Message<KafkaExternalTask>, String> template) {
        this.template = template;
    }

    @Async
    public void sendToKafka(Message<KafkaExternalTask> kafkaExternalTaskMessage) {

        ListenableFuture<SendResult<String, Message<KafkaExternalTask>>> future = template.send(kafkaExternalTaskMessage);
        future.addCallback(new ListenableFutureCallback<SendResult<String, Message<KafkaExternalTask>>>() {

            @Override
            public void onSuccess(SendResult<String, Message<KafkaExternalTask>> result) {
                log.info("Success :: " + ToStringBuilder.reflectionToString(result.toString()));
            }

            @Override
            public void onFailure(Throwable exception) {
                log.error(exception.getMessage());
            }
        });
    }

}
