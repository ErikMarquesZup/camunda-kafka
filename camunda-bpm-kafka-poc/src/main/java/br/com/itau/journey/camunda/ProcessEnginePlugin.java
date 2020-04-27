package br.com.itau.journey.camunda;

import br.com.itau.journey.constant.TypeComponent;
import br.com.itau.journey.domain.ExternalTaskAccessInfo;
import br.com.itau.journey.domain.KafkaExternalTask;
import br.com.itau.journey.kafka.schedulers.ExternalTaskScheduler;
import br.com.itau.journey.rocksdb.RocksDBKeyValueService;
import br.com.itau.journey.rocksdb.kv.exception.FindFailedException;
import br.com.itau.journey.rocksdb.mapper.exception.SerDeException;
import br.com.itau.journey.service.ProcessInstanceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.delegate.ExecutionListener;
import org.camunda.bpm.engine.delegate.TaskListener;
import org.camunda.bpm.engine.form.TaskFormData;
import org.camunda.bpm.engine.impl.bpmn.behavior.NoneEndEventActivityBehavior;
import org.camunda.bpm.engine.impl.bpmn.behavior.NoneStartEventActivityBehavior;
import org.camunda.bpm.engine.impl.bpmn.behavior.UserTaskActivityBehavior;
import org.camunda.bpm.engine.impl.bpmn.parser.AbstractBpmnParseListener;
import org.camunda.bpm.engine.impl.bpmn.parser.BpmnParseListener;
import org.camunda.bpm.engine.impl.cfg.AbstractProcessEnginePlugin;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.form.handler.TaskFormHandler;
import org.camunda.bpm.engine.impl.persistence.entity.TaskEntity;
import org.camunda.bpm.engine.impl.pvm.delegate.ActivityBehavior;
import org.camunda.bpm.engine.impl.pvm.process.ActivityImpl;
import org.camunda.bpm.engine.impl.pvm.process.ScopeImpl;
import org.camunda.bpm.engine.impl.util.xml.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

@Component
@Slf4j
public class ProcessEnginePlugin extends AbstractProcessEnginePlugin {

    public static final String EXTENSION_ELEMENTS = "extensionElements";
    public static final String PROPERTIES = "properties";
    public static final String PROPERTY = "property";
    public static final String NAME = "name";
    public static final String KAFKA_TOPIC = "kafkaTopic";
    public static final String INTERNAL_USER_TASK = "internalUserTask";
    public static final String VALUE = "value";
    public static final String COMMA = ",";
    @Autowired
    private ExternalTaskScheduler externalTaskScheduler;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ProcessEngine engine;
    @Autowired
    private RuntimeService runtimeService;
    @Autowired
    private RocksDBKeyValueService rocksDBKeyValueService;


    public final String START_EVENT = "start";
    public final String CREATE = "create";

    public ProcessEnginePlugin() {
    }

    public ProcessEnginePlugin(ExternalTaskScheduler externalTaskScheduler, ObjectMapper objectMapper, ProcessEngine engine, RuntimeService runtimeService, RocksDBKeyValueService rocksDBKeyValueService) {
        this.externalTaskScheduler = externalTaskScheduler;
        this.objectMapper = objectMapper;
        this.engine = engine;
        this.runtimeService = runtimeService;
        this.rocksDBKeyValueService = rocksDBKeyValueService;
    }

    private List<BpmnParseListener> customPreBPMNParseListeners(final ProcessEngineConfigurationImpl processEngineConfiguration) {
        if (processEngineConfiguration.getCustomPreBPMNParseListeners() == null) {
            processEngineConfiguration.setCustomPreBPMNParseListeners(new ArrayList<BpmnParseListener>());
        }
        return processEngineConfiguration.getCustomPreBPMNParseListeners();
    }

    @Override
    public void preInit(ProcessEngineConfigurationImpl processEngineConfiguration) {
        customPreBPMNParseListeners(processEngineConfiguration)
                .add(new RegisterExternalTaskBpmnParseListener());
    }

    public class RegisterExternalTaskBpmnParseListener extends AbstractBpmnParseListener {

        @Override
        public void parseStartEvent(Element startEvent, ScopeImpl scope, ActivityImpl activity) {
            ActivityBehavior activityBehavior = activity.getActivityBehavior();
            if (activityBehavior instanceof NoneStartEventActivityBehavior) {
                List<String> kafkaTopics = getTopics(startEvent);
                activity.addListener(START_EVENT, getExecutionListener(kafkaTopics, activity.getId(),TypeComponent.START_EVENT));
            }
        }

        @Override
        public void parseEndEvent(Element endProcess, ScopeImpl scope, ActivityImpl activity) {
            ActivityBehavior activityBehavior = activity.getActivityBehavior();
            if (activityBehavior instanceof NoneEndEventActivityBehavior) {
                List<String> kafkaTopics = getTopics(endProcess);
                activity.addListener(START_EVENT, getExecutionListener(kafkaTopics, activity.getId(),TypeComponent.END_EVENT));
            }
        }

        @Override
        public void parseUserTask(Element userTask, ScopeImpl scope, ActivityImpl activity) {
            ActivityBehavior activityBehavior = activity.getActivityBehavior();
            if (activityBehavior instanceof UserTaskActivityBehavior) {
                List<String> kafkaTopics = getTopics(userTask);

                boolean internalUserTask = getInternalUserTask(userTask);
                TypeComponent type = internalUserTask ? TypeComponent.SERVICE_TASK_EVENT : TypeComponent.USER_TASK;

                UserTaskActivityBehavior userTaskActivityBehavior = (UserTaskActivityBehavior) activityBehavior;
                userTaskActivityBehavior
                        .getTaskDefinition()
                        .addTaskListener(CREATE, getTaskListener(kafkaTopics, activity.getId(), type, internalUserTask));
            }
        }

        private List<String> getTopics(Element element) {
            return ofNullable(element.element(EXTENSION_ELEMENTS))
                    .map(getPropertiesElement())
                    .map(getPropertyList())
                    .map(getFilteredTopicList()).orElse(new ArrayList<>());
        }

        private boolean getInternalUserTask(Element element) {
            return !ofNullable(element.element(EXTENSION_ELEMENTS)).map(getPropertiesElement())
                                                                   .map(getPropertyList())
                                                                   .map(getFilteredInternalUserTask()).get().isEmpty();
        }

        private TaskListener getTaskListener(List<String> kafkaTopics, String typeDescription, TypeComponent type, boolean internalUserTask) {
            return execution -> {
                String processInstanceId = execution.getProcessInstanceId();
                String taskId = execution.getId();
                TaskFormHandler taskFormHandler = ((TaskEntity) execution).getTaskDefinition().getTaskFormHandler();
                TaskFormData taskForm = taskFormHandler.createTaskForm((TaskEntity) execution);
                String info = null;
                try {
                    info = objectMapper.writeValueAsString(taskForm.getFormFields()).replace("\\", "");
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }
                logExternalTaskInfo(kafkaTopics, processInstanceId, taskId, info);
                ExternalTaskAccessInfo externalTaskAccessInfo = ExternalTaskAccessInfo.builder()
                        .kafkaTopics(kafkaTopics)
                        .kafkaExternalTask(KafkaExternalTask.builder()
                                .processInstanceId(processInstanceId)
                                .typeDescription(typeDescription)
                                .taskId(taskId)
                                .infoUserTask(info)
                                .internalUserTask(internalUserTask)
                                .type(type.getEvent())
                                .build())
                        .build();
                externalTaskScheduler.scheduler(externalTaskAccessInfo);
            };
        }

        private ExecutionListener getExecutionListener(List<String> kafkaTopics, String typeDescription, TypeComponent type) {
            return execution -> {
                String processInstanceId = execution.getProcessInstanceId();
                String activityInstanceId = execution.getActivityInstanceId();
                String currentActivityId = execution.getCurrentActivityId();
                String taskId = execution.getId();

                logExternalTaskInfo(kafkaTopics, processInstanceId);
                ExternalTaskAccessInfo externalTaskAccessInfo = ExternalTaskAccessInfo.builder()
                        .kafkaTopics(kafkaTopics)
                        .kafkaExternalTask(KafkaExternalTask.builder()
                                .processInstanceId(processInstanceId)
                                .typeDescription(typeDescription)
                                .activityInstanceId(activityInstanceId)
                                .currentActivityId(currentActivityId)
                                .taskId(taskId)
                                .type(type.getEvent())
                                .build())
                        .build();
                externalTaskScheduler.scheduler(externalTaskAccessInfo);
            };
        }

        private void logExternalTaskInfo(List<String> topics, String processInstanceId) {
            logExternalTaskInfo(topics, processInstanceId, null, null);
        }

        private void logExternalTaskInfo(List<String> topics, String processInstanceId, String taskId, Object info) {
            log.info("processInstanceId: {}", processInstanceId);
            log.info("taskId: [{}]", taskId);
            log.info("kafkaTopcis: [{}]", getKafkaTopicList(topics));
            log.info("info: [{}]", info);
        }

        private String getKafkaTopicList(List<String> topics) {
            if (CollectionUtils.isEmpty(topics)) return StringUtils.EMPTY;
            StringBuilder topicsName = new StringBuilder();
            topics.forEach(s -> topicsName.append(s).append(COMMA));
            topicsName.deleteCharAt(topicsName.length() - 1);
            return topicsName.toString();
        }

        private Function<List<Element>, List<String>> getFilteredTopicList() {
            return propertyList ->
                    propertyList
                            .stream()
                            .filter(getTopicNamePredicate())
                            .map(getValue()).collect(Collectors.toList());
        }

        private Function<List<Element>, List<String>> getFilteredInternalUserTask() {
            return propertyList ->
                    propertyList
                            .stream()
                            .filter(getInternalTaskPredicate())
                            .map(getValue()).collect(Collectors.toList());
        }

        private Function<Element, String> getValue() {
            return property -> property.attribute(VALUE);
        }

        private Predicate<Element> getInternalTaskPredicate() {
            return property -> INTERNAL_USER_TASK.equals(property.attribute(NAME));
        }

        private Predicate<Element> getTopicNamePredicate() {
            return property -> StringUtils.startsWith(property.attribute(NAME), KAFKA_TOPIC);
        }

        private Function<Element, List<Element>> getPropertyList() {
            return propertiesElement -> propertiesElement.elements(PROPERTY);
        }

        private Function<Element, Element> getPropertiesElement() {
            return extensionElement -> extensionElement.element(PROPERTIES);
        }
    }
}
