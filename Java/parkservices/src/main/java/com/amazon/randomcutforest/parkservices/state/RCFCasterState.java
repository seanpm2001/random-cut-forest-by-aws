/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.amazon.randomcutforest.parkservices.state;

import static com.amazon.randomcutforest.state.Version.V3_8;

import lombok.Data;

import com.amazon.randomcutforest.parkservices.state.errorhandler.ErrorHandlerState;

@Data
public class RCFCasterState extends ThresholdedRandomCutForestState {
    private static final long serialVersionUID = 1L;
    private String version = V3_8;

    private int forecastHorizon;
    private ErrorHandlerState errorHandler;
    private int errorHorizon;
    private String calibrationMethod;
}
