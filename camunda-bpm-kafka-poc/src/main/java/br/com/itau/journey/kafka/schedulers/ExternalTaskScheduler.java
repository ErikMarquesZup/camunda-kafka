package br.com.itau.journey.kafka.schedulers;

import br.com.itau.journey.domain.ExternalTaskAccessInfo;
import br.com.itau.journey.domain.KafkaExternalTask;
import br.com.itau.journey.service.ProducerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

import static java.util.Optional.ofNullable;

@Component
public class ExternalTaskScheduler {

    private ProducerService producerService;

    @Autowired
    public ExternalTaskScheduler(ProducerService producerService) {
        this.producerService = producerService;
    }

    public void scheduler(ExternalTaskAccessInfo externalTaskAccessInfo) {
        ofNullable(externalTaskAccessInfo)
                .ifPresent(externalTaskAccessInfo1 -> ofNullable(externalTaskAccessInfo1.getKafkaTopics())
                        .ifPresent(kafkaTopicList -> kafkaTopicList.parallelStream().forEach(sendToKafkaTopic(externalTaskAccessInfo1))));
    }

    private Consumer<String> sendToKafkaTopic(ExternalTaskAccessInfo externalTaskAccessInfo1) {
        return kafkaTopic -> {
            Message<KafkaExternalTask> externalTaskMessage = getExternalTaskMessage(externalTaskAccessInfo1.getKafkaExternalTask(), kafkaTopic);
            producerService.sendToKafka(externalTaskMessage);
        };
    }

    public Message<KafkaExternalTask> getExternalTaskMessage(KafkaExternalTask kafkaExternalTask, String kafkaTopic) {
        return MessageBuilder
                .withPayload(kafkaExternalTask)
                .setHeader(KafkaHeaders.TOPIC, kafkaTopic)
                .build();
    }
}
