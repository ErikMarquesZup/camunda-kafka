package br.com.itau.journey.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class KafkaExternalTask {

    private String id = "12345";
    private boolean reactive;
    private String type;
    private String cpf;
    private String typeDescription;
    private boolean internalUserTask = true;
    private String uuid;
    private String bpmnInstance;
    private String processInstanceId;
    private String taskId;
    private boolean taskComplete;
    private String activityInstanceId;
    private String currentActivityId;
    private Object infoUserTask;

}
