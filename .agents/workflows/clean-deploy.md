---
description: create a clean fresh deployment on oracle
---
[01_oci_setup_guide.md](file;file:///Users/parthureddy/Documents/Food%20Delivery.nosync/Deployment/OracleDeployment/01_oci_setup_guide.md)[02_vm_init.sh](file;file:///Users/parthureddy/Documents/Food%20Delivery.nosync/Deployment/OracleDeployment/02_vm_init.sh)[03_deploy_all.sh](file;file:///Users/parthureddy/Documents/Food%20Delivery.nosync/Deployment/OracleDeployment/03_deploy_all.sh)[03_deploy_dev.sh](file;file:///Users/parthureddy/Documents/Food%20Delivery.nosync/Deployment/OracleDeployment/03_deploy_dev.sh)[ORACLE_CONFIG.md](file;file:///Users/parthureddy/Documents/Food%20Delivery.nosync/Deployment/OracleDeployment/ORACLE_CONFIG.md)  understand these thoroughly. make sure you keep local code changes in sync on remote oracle.

### Mistakes to Avoid (Learned from Past Failures)
- **Mistake 1**: Do NOT copy `target/*.jar` from a local directory directly unless you have run `mvn clean package`.
- **Mistake 2**: When recreating `config-service`, dependent services might fail with `Connection Refused` on port 8888 because they restart faster than the config-server initializes.
- **Mistake 3 (CRITICAL)**: When running `rsync` to a remote host containing spaces in the path (e.g. `"/home/ubuntu/Food Delivery.nosync/"`), if you pass multiple source directories, `rsync` will fail silently if the space is not properly escaped in the destination string (e.g., `ubuntu@host:"/home/ubuntu/Food\ Delivery.nosync/"`). This leaves remote JARs stale, and Docker will build old code! Always check `rsync` exit codes and escape spaces carefully!

Now what I need is to you to completely clean everything in oracle clound and deploy all services as fresh. Deploy as Dev profile. check logs and make sure every service started without any errors.

**CRITICAL RULE FOR AGENT:** Always monitor logs yourself for at least 2 minutes for EACH service after you run the deploy remote script. Do not blindly trust that the script finished successfully. This manual monitoring step is crucial for catching silent failures (e.g., Flyway exceptions, bean creation failures, database connection leaks) that happen during the application boot process.

add your learning to [GENERALIZED_DEPLOYMENT_STEPS.md](file;file:///Users/parthureddy/Documents/Food%20Delivery.nosync/Deployment/DeploymentSteps/GENERALIZED_DEPLOYMENT_STEPS.md) if you face any errors and the procedure you followed to fix them so that when I deploy new services I don't face same errors