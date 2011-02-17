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

import java.io.File;
import java.io.IOException;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

/**
 * Class for sending a local file to a remote machine using scp.
 * This class is based on code from Apache Ant and the jsch library.
 */
public class Scp extends SSHBase {

    private String fromUri;
    private String toUri;
    
    public static void main(String[] args) {
    	if( args.length != 3 ) {
    		System.out.println( "USAGE: <remote directory user@host:path> <private key file> <local file>");
    		System.exit(-1);
    	}
    	
		Scp scp = new Scp();
		scp.setTodir( args[0] );
		scp.setKeyfile( args[1] );
		scp.setFile(args[2] );
		scp.setTrust(true);
		scp.execute();
    }

    /**
     * Sets the file to be transferred. 
     * @param aFromUri a string representing the file to transfer.
     */
    public void setFile(String aFromUri) {
        setFromUri(aFromUri);
    }

    /**
     * Sets the location where files will be transferred to.
     * This can either be a remote directory or a local directory.
     * Remote directories take the form of:<br>
     * <i>user:password@host:/directory/path/</i><br>
     * This parameter is required.

     * @param aToUri a string representing the target of the copy.
     */
    public void setTodir(String aToUri) {
        setToUri(aToUri);
    }

    private static void validateRemoteUri(String type, String aToUri) {
    	if (!isRemoteUri(aToUri)) {
            throw new ScpException(type + " '" + aToUri + "' is invalid. "
                                     + "The 'remoteToDir' attribute must "
                                     + "have syntax like the "
                                     + "following: user:password@host:/path"
                                     + " - the :password part is optional");
    	}
    } 

    /**
     * Changes the file name to the given name while sending it,
     * only useful if sending a single file.
     * @param aToUri a string representing the target of the copy.
     * @since Ant 1.6.2
     */
    public void setRemoteTofile(String aToUri) {
        validateRemoteUri("remoteToFile", aToUri);
        setToUri(aToUri);
    }

    /**
     * Initialize this task.
     * @throws ScpException on error
     */
    public void init() throws ScpException {
        super.init();
        this.toUri = null;
        this.fromUri = null;
    }

    /**
     * Execute this task.
     * @throws ScpException on error
     */
    public void execute() throws ScpException {
        
    	try {
    		upload(fromUri, toUri);
        } catch (Exception e) {
            if (getFailonerror()) {
                if(e instanceof ScpException) {
                    ScpException be = (ScpException) e;
                    throw be;
                } else {
                    throw new ScpException(e);
                }
            } else {
                log("Caught exception: " + e.getMessage());
            }
        }
    }

    private void upload(String fromPath, String toSshUri)
        throws IOException, JSchException {
        String file = parseUri(toSshUri);

        Session session = null;
        try {
            session = openSession();
            ScpToMessage message = null;
                message =
                    new ScpToMessage(session,
                                     new File(fromPath), file);
            message.execute();
        } finally {
            if (session != null) {
                session.disconnect();
            }
        }
    }

    private String parseUri(String uri) {

        int indexOfAt = uri.indexOf('@');
        int indexOfColon = uri.indexOf(':');

        if (indexOfColon > -1 && indexOfColon < indexOfAt) {
            // user:password@host:/path notation
            // everything upto the last @ before the last : is considered
            // password. (so if the path contains an @ and a : it will not work)
            int indexOfCurrentAt = indexOfAt;
            int indexOfLastColon = uri.lastIndexOf(':');
            while (indexOfCurrentAt > -1 && indexOfCurrentAt < indexOfLastColon)
            {
                indexOfAt = indexOfCurrentAt;
                indexOfCurrentAt = uri.indexOf('@', indexOfCurrentAt + 1);
            }
            setUsername(uri.substring(0, indexOfColon));
            setPassword(uri.substring(indexOfColon + 1, indexOfAt));
        } else if (indexOfAt > -1) {
            // no password, will require keyfile
            setUsername(uri.substring(0, indexOfAt));
        } else {
            throw new ScpException("no username was given.  Can't authenticate."); 
        }

        if (getUserInfo().getPassword() == null
            && getUserInfo().getKeyfile() == null) {
            throw new ScpException("neither password nor keyfile for user "
                                     + getUserInfo().getName() + " has been "
                                     + "given.  Can't authenticate.");
        }

        int indexOfPath = uri.indexOf(':', indexOfAt + 1);
        if (indexOfPath == -1) {
            throw new ScpException("no remote path in " + uri);
        }

        setHost(uri.substring(indexOfAt + 1, indexOfPath));
        String remotePath = uri.substring(indexOfPath + 1);
        if (remotePath.equals("")) {
            remotePath = ".";
        }
        return remotePath;
    }

    private static boolean isRemoteUri(String uri) {
        boolean isRemote = true;
        int indexOfAt = uri.indexOf('@');
        if (indexOfAt < 0) {
            isRemote = false;
        }
        return isRemote;
    }

    private void setFromUri(String fromUri) {
        this.fromUri = fromUri;
    }

    private void setToUri(String toUri) {
        this.toUri = toUri;
    }
}
