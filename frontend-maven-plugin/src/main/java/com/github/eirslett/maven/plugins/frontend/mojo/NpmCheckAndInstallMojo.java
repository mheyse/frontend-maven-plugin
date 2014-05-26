package com.github.eirslett.maven.plugins.frontend.mojo;

import static com.github.eirslett.maven.plugins.frontend.mojo.MojoUtils.setSLF4jLogger;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.eirslett.maven.plugins.frontend.lib.FrontendPluginFactory;
import com.github.eirslett.maven.plugins.frontend.lib.NpmRunner;
import com.github.eirslett.maven.plugins.frontend.lib.ProxyConfig;
import com.github.eirslett.maven.plugins.frontend.lib.TaskRunnerException;

@Mojo(name="npm-check-and-install",  defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class NpmCheckAndInstallMojo extends AbstractMojo {
	/**
     * The base directory for running all Node commands. (Usually the directory that contains package.json)
     */
    @Parameter(defaultValue = "${basedir}", property = "workingDirectory", required = false)
    private File workingDirectory;

    /**
     * Modules to install. Format: "module1@version1[ module2@version2[ ...]]"
     */
    @Parameter(property = "arguments", required = true)
    private String arguments;

    @Parameter(property = "session", defaultValue = "${session}", readonly = true)
    private MavenSession session;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            setSLF4jLogger(getLog());
            Logger logger = LoggerFactory.getLogger(getClass());
            
            ProxyConfig proxyConfig = MojoUtils.getProxyConfig(session);
            NpmRunner npmRunner = new FrontendPluginFactory(workingDirectory, proxyConfig).getNpmRunner();
            
            Map<String, String> modulesToInstall = new HashMap<String, String>();
            Map<String, String> installedModules = new HashMap<String, String>();
            StringBuilder installArgs = new StringBuilder();
            
            String[] modulesDesc = arguments.split(" ");
            for(String moduleDesc: modulesDesc) {
            	String[] module = moduleDesc.split("@");
            	if(module.length != 2)
            		throw new MojoFailureException("Invalid argument format for parameter 'arguments'. Must be 'module1@version1[ module2@version2[ ...]]'.");
            	
            	String moduleName = module[0];
            	String moduleVersion = module[1];
            	modulesToInstall.put(moduleName, moduleVersion);
            }
            
            String installedModulesJsonString = npmRunner.executeAndGetResult("ls -json");
            ObjectNode installedModulesJson = (ObjectNode) new ObjectMapper().readTree(installedModulesJsonString);
            Iterator<Entry<String, JsonNode>> it = installedModulesJson.path("dependencies").getFields();
            while(it.hasNext()) {
            	Entry<String, JsonNode> module = it.next();
            	String moduleName = module.getKey();
            	String moduleVersion = module.getValue().path("version").asText();
            	installedModules.put(moduleName, moduleVersion);
            }
            
            for(Entry<String, String> e: installedModules.entrySet()) {
            	String moduleName = e.getKey();
            	String moduleVersion = e.getValue();
            	logger.debug("installed: " + moduleName + " (" + moduleVersion + ")");
            }
            
            for(Entry<String, String> e: modulesToInstall.entrySet()) {
            	String moduleName = e.getKey();
            	String moduleVersion = e.getValue();
            	if(!moduleVersion.equals(installedModules.get(moduleName))) {
            		logger.debug("to install: " + moduleName + " (" + moduleVersion + ")");
            		installArgs.append(" ");
            		installArgs.append(moduleName);
            		installArgs.append("@");
            		installArgs.append(moduleVersion);
            	} else {
            		logger.debug("skipping: " + moduleName + " (" + moduleVersion + ")");
            	}
            }
            
            if(installArgs.length() > 0) {
            	logger.debug("npm command: 'install" + installArgs.toString() + "'");
            	npmRunner.execute("install" + installArgs.toString());
            } else {
            	logger.info("npm-check-and-install: nothing to install.");
            }
        } catch (TaskRunnerException e) {
            throw new MojoFailureException(e.getMessage());
        } catch (IOException e) {
        	throw new MojoFailureException(e.getMessage());
		}
    }
}
