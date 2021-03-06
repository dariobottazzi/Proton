/*******************************************************************************
 * Copyright 2014 IBM
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.ibm.hrl.proton.server.executor;


import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Calendar;
import java.util.Collection;
import java.util.logging.Logger;

import com.ibm.hrl.proton.agentQueues.queuesManagement.AgentQueuesManager;
import com.ibm.hrl.proton.context.facade.ContextServiceFacade;
import com.ibm.hrl.proton.epaManager.EPAManagerFacade;
import com.ibm.hrl.proton.eventHandler.EventHandler;
import com.ibm.hrl.proton.eventHandler.IEventHandler;
import com.ibm.hrl.proton.expression.facade.EepFacade;
import com.ibm.hrl.proton.metadata.parser.MetadataParser;
import com.ibm.hrl.proton.metadata.parser.ParsingException;
import com.ibm.hrl.proton.metadata.parser.ProtonParseException;
import com.ibm.hrl.proton.router.EventRouter;
import com.ibm.hrl.proton.router.IEventRouter;
import com.ibm.hrl.proton.runtime.metadata.IMetadataFacade;
import com.ibm.hrl.proton.runtime.metadata.MetadataFacade;
import com.ibm.hrl.proton.server.adapter.InputServer;
import com.ibm.hrl.proton.server.adapter.OutputServer;
import com.ibm.hrl.proton.server.adapter.ProtonServerException;
import com.ibm.hrl.proton.server.adapter.eventHandlers.StandaloneDataSender;
import com.ibm.hrl.proton.server.executorServices.ExecutorUtils;
import com.ibm.hrl.proton.server.timerService.TimerServiceFacade;
import com.ibm.hrl.proton.server.workManager.WorkManagerFacade;
import com.ibm.hrl.proton.utilities.facadesManager.FacadesManager;
import com.ibm.hrl.proton.utilities.facadesManager.IFacadesManager;




public class ProtonExecutor
{



	
	private static final int BACKLOG_SIZE = 1000;
	private static final Logger logger = Logger.getLogger(ProtonExecutor.class.getName());	 
	private static Calendar calendar  = Calendar.getInstance();
	

    public static void main(String[] args) throws ProtonServerException
    {
         
    	PropertiesParser prop = null;
    	InputServer inputServer=null;
    	OutputServer outputServer=null ;
    	IFacadesManager facadesManager = new FacadesManager();
    	IMetadataFacade metadataFacade = new MetadataFacade();
    	EepFacade eepFacade = null;
    	try
    	{
    		eepFacade = new EepFacade();    		
    		((FacadesManager)facadesManager).setEepFacade(eepFacade);
    	
    		String propertiesFileNamePath = "./config/Proton.properties";
    	
    		
			//check for input parameter - the properties file name.    		
    		if (args.length != 0)
    			propertiesFileNamePath=args[0];
    	
    		prop = new PropertiesParser(propertiesFileNamePath);            
            inputServer = new InputServer(prop.inputPortNumber,BACKLOG_SIZE,facadesManager,metadataFacade,eepFacade);
            outputServer = new OutputServer(prop.outputPortNumber,BACKLOG_SIZE,facadesManager,metadataFacade,eepFacade);
           
            logger.info("init: initializing metadata and all the system singletons");
            
    	}
    	catch (Exception e)
        {
            String msg = "Could not configure application ,reason : " +e+" message: "+e.getMessage();
            logger.severe(msg);
            
            System.exit(1);
        }
        
    	
        
        
        try
        {
            
        	
        	
        	
        	Collection<ProtonParseException> exceptions = initializeMetadata(prop.metadataFileName,eepFacade,metadataFacade);
        	logger.info("init: done initializing metadata, returned the following exceptions: ");
        	for (ProtonParseException protonParseException : exceptions) {
				logger.info(protonParseException.toString());
			}
        	
            TimerServiceFacade timerServiceFacade = new TimerServiceFacade();
            ((FacadesManager)facadesManager).setTimerServiceFacade(timerServiceFacade);
            IEventHandler eventHandler = new EventHandler(facadesManager,metadataFacade);
            ((FacadesManager)facadesManager).setEventHandler(eventHandler);
            StandaloneDataSender dataSender = new StandaloneDataSender();
            ((FacadesManager)facadesManager).setDataSender(dataSender);
            IEventRouter eventRouter = new EventRouter(dataSender,facadesManager,metadataFacade);
            ((FacadesManager)facadesManager).setEventRouter(eventRouter);
            WorkManagerFacade workManagerFacade = new WorkManagerFacade();
            ((FacadesManager)facadesManager).setWorkManager(workManagerFacade);
            EPAManagerFacade epaManagerFacade = new EPAManagerFacade(workManagerFacade, eventRouter, null,metadataFacade);
            ((FacadesManager)facadesManager).setEpaManager(epaManagerFacade);
            AgentQueuesManager agentQueuesManager = new AgentQueuesManager(timerServiceFacade, eventHandler, workManagerFacade,metadataFacade);
            ((FacadesManager)facadesManager).setAgentQueuesManager(agentQueuesManager);
            ContextServiceFacade contextService = new ContextServiceFacade(timerServiceFacade, eventHandler,metadataFacade.getContextMetadataFacade());
            ((FacadesManager)facadesManager).setContextServiceFacade(contextService);                    
               
            
            
            logger.info("init: done initializing singletons , starting the servers...");
            outputServer.startServer();
            inputServer.startServer();            
            
            //wait for user's input to shutdown the server
            System.out.print("Press any key for servers shutdown: ");

            //  open up standard input
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

            String command = null;

            //  read the command from the command-line; need to use try/catch with the
            //  readLine() method
            try {
               command = br.readLine();
            } catch (IOException ioe) {              
               System.exit(1);
            }

            inputServer.stopServer();
            outputServer.stopServer();
            timerServiceFacade.destroyTimers();
            ExecutorUtils.shutdownNow();

                        
        }       
        catch (Exception e)
        {
            String msg = "Could not initialize application ,reason : " +e+"message: "+e.getMessage();
            logger.severe(msg);
            inputServer.stopServer();
            outputServer.stopServer();
            System.exit(1);
        }
        

    }

       	
    private static Collection<ProtonParseException> initializeMetadata(String metadataFileName, EepFacade eep,IMetadataFacade metadataFacade) throws ParsingException{
        
   	 String line;
		 StringBuilder sb = new StringBuilder();
	     BufferedReader in = null;
	     String jsonFileName = metadataFileName;
	     try
	     {
	    	 in = new BufferedReader(new InputStreamReader(new FileInputStream(jsonFileName), "UTF-8"));	    
	    	 while ((line = in.readLine()) != null)
	    	 {
	    		 sb.append(line);
	    	 }

	     }catch(Exception e)
	     {
	    	 e.printStackTrace();
	     }finally
	     {
	    	 try {
				in.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	     }
	     
	     String jsonTxt  = sb.toString();

	    	   
       MetadataParser metadataParser  = new MetadataParser(eep,metadataFacade);        
       Collection<ProtonParseException> exceptions = metadataParser.parseEPN(jsonTxt);
       return exceptions;
             
   }
}
