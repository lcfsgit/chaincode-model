/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.samples.assettransfer;
import java.util.Objects;

import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;

import com.owlike.genson.annotation.JsonProperty;

@DataType()
public final class Task {

    @Property()
    private final String taskID;

    @Property()
    private final String taskDetail;

    @Property()
    private final String taskCreatorId;

    @Property()
    private final String taskReceiverId;

    @Property()
    private final String receiveTime;

    @Property()
    private final String taskScore;


    public String getTaskID() {
        return taskID;
    }

    public String getTaskDetail() {
        return taskDetail;
    }

    public String getTaskCreatorId() {
        return taskCreatorId;
    }

    public String getTaskReceiverId() {
        return taskReceiverId;
    }

    public String getReceiveTime() {
        return receiveTime;
    }

    public String getTaskScore() {
        return taskScore;
    }

    public Task(@JsonProperty("taskID") final String taskID, @JsonProperty("taskDetail") final String taskDetail, @JsonProperty("taskCreatorId") final String taskCreatorId,
                @JsonProperty("taskReceiverId") final String taskReceiverId, @JsonProperty("receiveTime") final String receiveTime,
                @JsonProperty("taskScore") final String taskScore) {
        this.taskID = taskID;
        this.taskDetail = taskDetail;
        this.taskCreatorId = taskCreatorId;
        this.taskReceiverId = taskReceiverId;
        this.receiveTime = receiveTime;
        this.taskScore = taskScore;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }

        if ((obj == null) || (getClass() != obj.getClass())) {
            return false;
        }

        Task other = (Task) obj;

        return Objects.deepEquals(
                new String[] {getTaskID(), getTaskCreatorId(), getTaskReceiverId(), getReceiveTime(), getTaskScore()},
                new String[] {other.getTaskID(), other.getTaskCreatorId(), other.getTaskReceiverId(), other.getTaskScore()});
    }

    @Override public int hashCode() {
        return Objects.hash(taskID, taskDetail, taskCreatorId, taskReceiverId, receiveTime, taskScore);
    }


    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "@" + Integer.toHexString(hashCode()) + " [taskID=" + taskID  + ", taskDetail=" + taskDetail + ", taskCreatorId=" + taskCreatorId + ", taskReceiverId=" + taskReceiverId + ", receiveTime=" + receiveTime + ", taskScore=" + taskScore + "]";
    }
}
