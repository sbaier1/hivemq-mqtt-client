/*
 * Copyright 2018-present HiveMQ and the HiveMQ Community
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
 */

package com.hivemq.client.mqtt;

import com.hivemq.client.annotations.CheckReturnValue;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Builder for a {@link MqttExecutorConfig}.
 *
 * @author Silvio Giebl
 * @since 1.0
 */
@ApiStatus.NonExtendable
public interface MqttExecutorConfigBuilder extends MqttExecutorConfigBuilderBase<MqttExecutorConfigBuilder> {

    /**
     * Builds the {@link MqttExecutorConfig}.
     *
     * @return the built {@link MqttExecutorConfig}.
     */
    @CheckReturnValue
    @NotNull MqttExecutorConfig build();

    /**
     * Builder for a {@link MqttExecutorConfig} that is applied to a parent.
     *
     * @param <P> the type of the result when the built {@link MqttExecutorConfig} is applied to the parent.
     */
    @ApiStatus.NonExtendable
    interface Nested<P> extends MqttExecutorConfigBuilderBase<Nested<P>> {

        /**
         * Builds the {@link MqttExecutorConfig} and applies it to the parent.
         *
         * @return the result when the built {@link MqttExecutorConfig} is applied to the parent.
         */
        @NotNull P applyExecutorConfig();
    }
}