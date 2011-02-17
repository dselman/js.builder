/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  Contributors:
 *  Daniel Selman - Derived work from Apache Ant, removed everything but scp upload
 *
 */
package org.selman.scp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

/**
 * Abstract class for ssh upload and download
 */
public abstract class AbstractSshMessage {

    private Session session;
    private LogListener listener = new LogListener() {
        public void log(String message) {
            // do nothing;
        }
    };

    /**
     * Constructor for AbstractSshMessage
     * @param session the ssh session to use
     */
    public AbstractSshMessage(Session session) {
        this.session = session;
    }

    /**
     * Open an ssh channel.
     * @param command the command to use
     * @return the channel
     * @throws JSchException on error
     */
    protected Channel openExecChannel(String command) throws JSchException {
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);

        return channel;
    }

    /**
     * Send an ack.
     * @param out the output stream to use
     * @throws IOException on error
     */
    protected void sendAck(OutputStream out) throws IOException {
        byte[] buf = new byte[1];
        buf[0] = 0;
        out.write(buf);
        out.flush();
    }

    /**
     * Reads the response, throws a BuildException if the response
     * indicates an error.
     * @param in the input stream to use
     * @throws IOException on I/O error
     * @throws ScpException on other errors
     */
    protected void waitForAck(InputStream in)
        throws IOException, ScpException {
        int b = in.read();

        // b may be 0 for success,
        //          1 for error,
        //          2 for fatal error,

        if (b == -1) {
            // didn't receive any response
            throw new ScpException("No response from server");
        } else if (b != 0) {
            StringBuffer sb = new StringBuffer();

            int c = in.read();
            while (c > 0 && c != '\n') {
                sb.append((char) c);
                c = in.read();
            }

            if (b == 1) {
                throw new ScpException("server indicated an error: "
                                         + sb.toString());
            } else if (b == 2) {
                throw new ScpException("server indicated a fatal error: "
                                         + sb.toString());
            } else {
                throw new ScpException("unknown response, code " + b
                                         + " message: " + sb.toString());
            }
        }
    }

    /**
     * Carry out the transfer.
     * @throws IOException on I/O errors
     * @throws JSchException on ssh errors
     */
    public abstract void execute() throws IOException, JSchException;

    /**
     * Log a message to the log listener.
     * @param message the message to log
     */
    protected void log(String message) {
        listener.log(message);
    }
}
