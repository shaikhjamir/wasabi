/*******************************************************************************
 * Copyright 2016 Intuit
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
 *******************************************************************************/
package com.intuit.wasabi.exceptions;

import java.util.Date;

import org.junit.Assert;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import com.intuit.wasabi.experimentobjects.Experiment;

@RunWith(MockitoJUnitRunner.class)
public class EndTimeHasPassedExceptionTest {

    private Experiment.ID experimentID = Experiment.ID.newInstance();
    private Date endTime = new Date();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testEndTimeHasPassedException1() {
        thrown.expect(EndTimeHasPassedException.class);
        throw new EndTimeHasPassedException(experimentID, endTime);
    }

    @Test
    public void testEndTimeHasPassedException2() {
        thrown.expect(EndTimeHasPassedException.class);
        throw new EndTimeHasPassedException(experimentID, endTime, new Throwable());
    }

}
