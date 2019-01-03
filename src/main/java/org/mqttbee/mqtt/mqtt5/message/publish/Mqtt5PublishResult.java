/*
 * Copyright 2018 The MQTT Bee project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.mqttbee.mqtt.mqtt5.message.publish;

import org.jetbrains.annotations.NotNull;
import org.mqttbee.annotations.DoNotImplement;
import org.mqttbee.mqtt.mqtt5.message.publish.puback.Mqtt5PubAck;
import org.mqttbee.mqtt.mqtt5.message.publish.pubcomp.Mqtt5PubComp;
import org.mqttbee.mqtt.mqtt5.message.publish.pubrec.Mqtt5PubRec;
import org.mqttbee.mqtt.mqtt5.message.publish.pubrel.Mqtt5PubRel;

import java.util.Optional;

/**
 * @author Silvio Giebl
 */
@DoNotImplement
public interface Mqtt5PublishResult {

    @NotNull Mqtt5Publish getPublish();

    @NotNull Optional<Throwable> getError();

    interface Mqtt5Qos1Result extends Mqtt5PublishResult {

        @NotNull Mqtt5PubAck getPubAck();
    }

    interface Mqtt5Qos2Result extends Mqtt5PublishResult {

        @NotNull Mqtt5PubRec getPubRec();
    }

    interface Mqtt5Qos2CompleteResult extends Mqtt5Qos2Result {

        @NotNull Mqtt5PubRel getPubRel();

        @NotNull Mqtt5PubComp getPubComp();
    }
}