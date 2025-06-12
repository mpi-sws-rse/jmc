package org.mpisws.jmc.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.mpisws.jmc.checker.exceptions.JmcCheckerException;
import org.mpisws.jmc.runtime.scheduling.SchedulingChoice;
import org.mpisws.jmc.runtime.scheduling.SchedulingChoiceValue;
import org.mpisws.jmc.runtime.scheduling.SchedulingChoiceValueFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class FileUtil {
    public static void unsafeStoreToFile(String path, String content) {
        try {
            Files.write(Paths.get(path), content.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static FileOutputStream unsafeCreateFile(String path) {
        try {
            Path pPath = Paths.get(path);
            if (Files.exists(pPath)) {
                Files.delete(pPath);
            }
            return new FileOutputStream(path);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Ensure the path exists, creating it if it does not.
     *
     * <p>Deletes the path if it already exists.
     *
     * @param path the path to ensure
     */
    public static void unsafeEnsurePath(String path) {
        try {
            Path pPath = Paths.get(path);
            if (Files.exists(pPath) && Files.isDirectory(pPath)) {
                Files.list(pPath)
                        .forEach(
                                (p) -> {
                                    try {
                                        Files.delete(p);
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                });
            }
            Files.deleteIfExists(pPath);
            Files.createDirectories(pPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void storeTaskSchedule(String filePath, List<? extends SchedulingChoice<?>> taskSchedule) throws JmcCheckerException {
        JsonArray schedule = new JsonArray();
        for (SchedulingChoice<?> choice: taskSchedule) {
            JsonObject choiceJson = new JsonObject();
            choiceJson.addProperty("taskId", choice.getTaskId());
            choiceJson.addProperty("isBlockTask", choice.isBlockTask());
            choiceJson.addProperty("isBlockExecution", choice.isBlockExecution());
            SchedulingChoiceValue value = choice.getValue();
            if (value != null) {
                JsonObject valueObject = new JsonObject();
                valueObject.addProperty("type", value.type());
                valueObject.add("content", value.toJson());
                choiceJson.add("value", valueObject);
            }
            schedule.add(choiceJson);
        }
        JsonObject scheduleJson = new JsonObject();
        scheduleJson.add("schedule", schedule);
        try {
            Files.writeString(Paths.get(filePath), scheduleJson.toString());
        } catch (IOException e) {
            throw new JmcCheckerException("Failed to store task schedule to file: " + filePath, e);
        }
    }

    public static List<SchedulingChoice<?>> readTaskSchedule(String filePath) throws JmcCheckerException {
        try {
            String content = Files.readString(Paths.get(filePath));
            JsonObject jsonObject = JsonParser.parseString(content).getAsJsonObject();
            JsonArray scheduleArray = jsonObject.getAsJsonArray("schedule");
            List<SchedulingChoice<?>> out = new ArrayList<>();
            for (int i = 0; i < scheduleArray.size(); i++) {
                JsonObject choiceJson = scheduleArray.get(i).getAsJsonObject();
                Long taskId = choiceJson.get("taskId").getAsLong();
                boolean isBlockTask = choiceJson.get("isBlockTask").getAsBoolean();
                if (isBlockTask) {
                    out.add(SchedulingChoice.blockTask(taskId));
                    continue;
                }
                boolean isBlockExecution = choiceJson.get("isBlockExecution").getAsBoolean();
                if (isBlockExecution) {
                    out.add(SchedulingChoice.blockExecution());
                    continue;
                }
                if (!choiceJson.has("value")) {
                    out.add(SchedulingChoice.task(taskId));
                    continue;
                }
                JsonObject valueJson = choiceJson.getAsJsonObject("value");
                String choiceValueType = valueJson.get("type").getAsString();
                if (!SchedulingChoiceValueFactory.containsType(choiceValueType)) {
                    throw new JmcCheckerException("No adapter registered for type: " + choiceValueType);
                }
                SchedulingChoiceValue value = SchedulingChoiceValueFactory.create(choiceValueType, choiceJson.get("content"));
                out.add(SchedulingChoice.task(taskId, value));
            }
            return out;
        } catch (IOException e) {
            throw new JmcCheckerException("Failed to read task schedule from file: " + filePath, e);
        }
    }
}
