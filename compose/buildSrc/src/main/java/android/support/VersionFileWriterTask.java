/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.support;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Task that allows to write a version to a given output file.
 */
public class VersionFileWriterTask extends DefaultTask {
    private String mVersion;
    private File mOutputFile;

    @Input
    public String getVersion() {
        return mVersion;
    }

    public void setVersion(String version) {
        mVersion = version;
    }

    @OutputFile
    public File getOutputFile() {
        return mOutputFile;
    }

    public void setOutputFile(File outputFile) {
        mOutputFile = outputFile;
    }

    /**
     * The main method for actually writing out the file.
     *
     * @throws IOException
     */
    @TaskAction
    public void run() throws IOException {
        PrintWriter writer = new PrintWriter(mOutputFile);
        writer.println(mVersion);
        writer.close();
    }
}
