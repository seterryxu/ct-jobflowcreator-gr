/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License") you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//      Contributors:      Xu Lijia

package ci.xlj.tools.jobflowcreator

import org.apache.log4j.Logger

import ci.xlj.libs.jenkinsvisitor.JenkinsVisitor
import ci.xlj.libs.utils.ConfigUtils
import ci.xlj.tools.jobflowcreator.config.ConfigLoader
import ci.xlj.tools.jobflowcreator.config.Globals

class JobFlowCreator {

	private static Logger logger = Logger.getLogger(JobFlowCreator.class)

	private def showCopyRight() {
		println
		'''Job Flow Creator v1.0.0 of 4 Jul. 2014, by Mr. Xu Lijia.
		   Send bug reports via email icbcsdcpmoxulj@outlook.com
		   This tool is used to create a complete job flow on Jenkins Server at a time.'''
	}

	/**                                                                                                                                                 
	 * show help message                                                                                                                                
	 */                                                                                                                                                 
	private def showUsage() {
		println
		'''Usage:
		   
		   1. Start from command line
		      java -jar jobflowcreator.jar <First_Job_Name_in_the_Flow> <New_Value_for_the_Replaced_Segment>
		   OR
           2. Called by pi-jobflowcreator plugin
              java -jar jobflowcreator.jar <First_Job_Name_in_the_Flow> \
                                           <Job_Name_Segment_Pattern> <New_Value_for_the_Replaced_Segment> \
										   <Jenkins_Server_Url> <Username> <Password> \
										   <Jobs_directory>
		      Create a new version for the specific job flow.'''
	}

	static main(args) {
		new JobFlowCreator(args)
	}

	private String url
	private String username
	private String password
	private String jobsDir

	private JobFlowCreator(String[] args) {
		showCopyRight()

		if (!args) {
			showUsage()
			System.exit(0)
		}

		if (args.length == 7) {
			Globals.START_FROM_CMD = false

			url = args[3]
			username = args[4]
			password = args[5]
			jobsDir=args[6]

			init()
			createJobFlow(args[0], args[1], args[2])

		} else if (args.length == 2) {
			Globals.START_FROM_CMD = true

			ConfigLoader.load()
			init()
			createJobFlow(args[0], Globals.JOB_NAME_PATTERN,Globals.REPLACED_PATTERN)

		} else {
			println 'Invalid parameters. See usage for details.'
			showUsage()
			System.exit(-1)
		}

		println '\nProcess completed.'
	}


	private JenkinsVisitor v

	private void init() {
		boolean isLogin

		if (Globals.START_FROM_CMD) {
			v = new JenkinsVisitor(Globals.URL)
			isLogin = v.login(Globals.USERNAME, Globals.PASSWORD)
		} else {
			v = new JenkinsVisitor(url)
			isLogin = v.login(username,password)
		}

		if (!isLogin) {
			println v.responseContent
			System.exit(-2)
		}
	}

	private def createJobFlow(firstJobName, jobNamePattern,replacement) {
		buildATree(firstJobName)
		// traverse the tree
		traverseTree(jobNamePattern,replacement)
	}

	private JobNode root

	private def buildATree(String firstJobName) {
		if (!v.getJobNameList().contains(firstJobName)) {
			println "Job ${firstJobName} does not exist."
			System.exit(-3)
		}

		root = new JobNode(firstJobName,
				ConfigUtils.getConfigFile((Globals.START_FROM_CMD ? Globals.JOBS_DIR:jobsDir)
				+ File.separator + firstJobName))

		recursivelyAdd(root)
	}

	/**                                                                                                                                                 
	 * recursive add tree nodes                                                                                                                          
	 */                                                                                                                                                 
	private def recursivelyAdd(JobNode node) {
		v.getDownStreamJobNameList(node.jobName).each{
			def tempNode=new JobNode(it, ConfigUtils.getConfigFile((Globals.START_FROM_CMD?Globals.JOBS_DIR:jobsDir)+File.separator+it))
			node.addASuccessor(tempNode)
			recursivelyAdd(tempNode)
		}
	}

	private def traverseTree(jobNamePattern,replacement) {
		if (root) {
			recursivelyVisit(root, jobNamePattern,replacement)
		}
	}

	def xslurper

	/**                                                                                                                                                 
	 * recursive visit tree node                                                                                                                        
	 */                                                                                                                                                 
	private def recursivelyVisit(node, jobNamePattern,replacement) {
		def newJobName = node.jobName.replaceFirst(jobNamePattern,
				replacement)

		def configXml=new XmlSlurper().parse(node.config)
		configXml.childProjects=configXml.childProjects.text().replaceAll(jobNamePattern, replacement)

		def result = v.create(newJobName, configXml)

		if (result == 200) {
			println "Job ${newJobName} created successfully."
			logger.info "Job ${newJobName} created successfully."
		} else {
			println "Error in creating job ${newJobName}. See log for details."

			def res = v.responseContent
			if (res.contains('A job already exists')) {
				logger.error("Job ${newJobName} already exists.")
			} else {
				logger.error(res)
			}
		}

		node.getSuccessors().each{
			recursivelyVisit(it, jobNamePattern,replacement)
		}
	}
}