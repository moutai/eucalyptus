#include <stdio.h>
#include <stdlib.h>
#define __USE_GNU /* strnlen */
#include <string.h> /* strlen, strcpy */
#include <time.h>
#include <limits.h> /* INT_MAX */
#include <sys/types.h> /* fork */
#include <sys/wait.h> /* waitpid */
#include <unistd.h>
#include <fcntl.h>
#include <assert.h>
#include <errno.h>
#include <sys/stat.h>
#include <pthread.h>
#include <sys/vfs.h> /* statfs */
#include <signal.h> /* SIGINT */

#include "ipc.h"
#include "misc.h"
#include <handlers.h>
#include <storage.h>
#include <eucalyptus.h>
#include <libvirt/libvirt.h>
#include <libvirt/virterror.h>
#include <vnetwork.h>
#include <euca_auth.h>


/* coming from handlers.c */
extern sem * xen_sem;
extern sem * inst_sem;
extern bunchOfInstances * global_instances;

/* temporary: will be cleaned out*/
static struct nc_state_t *nc = NULL;

static int
doInitialize (struct nc_state_t *parent_nc) 
{
	if (!parent_nc)
		return ERROR_FATAL;

	nc = parent_nc;

	return OK;
}

static int
doRunInstance (	ncMetadata *meta, char *instanceId,
		char *reservationId, ncInstParams *params, 
		char *imageId, char *imageURL, 
		char *kernelId, char *kernelURL, 
		char *ramdiskId, char *ramdiskURL, 
		char *keyName, char *privMac, char *pubMac, int vlan, 
		char *userData, char *launchIndex, 
		char **groupNames, int groupNamesSize, ncInstance **outInst)
{
	return ERROR_FATAL;
}

static int
doRebootInstance(ncMetadata *meta, char *instanceId) 
{    
    return ERROR_FATAL;
}

static int
doGetConsoleOutput(	ncMetadata *meta,
			char *instanceId,
			char **consoleOutput)
{
	return ERROR_FATAL;
}

static int
doTerminateInstance(	ncMetadata *meta,
			char *instanceId,
			int *shutdownState,
			int *previousState)
{
	ncInstance *instance, *vninstance;
	virConnectPtr *conn;
	int err;

	logprintfl (EUCAINFO, "doTerminateInstance() invoked (id=%s)\n", instanceId);

	sem_p (inst_sem); 
	instance = find_instance(&global_instances, instanceId);
	sem_v (inst_sem);
	if (instance == NULL) 
		return NOT_FOUND;

	/* try stopping the KVM domain */
	conn = check_hypervisor_conn();
	if (conn) {
		virDomainPtr dom = virDomainLookupByName(*conn, instanceId);
		if (dom) {
			/* also protect 'destroy' commands, just in case */
			sem_p (xen_sem);
			err = virDomainDestroy (dom);
			sem_v (xen_sem);
			if (err==0) {
				logprintfl (EUCAINFO, "destroyed domain for instance %s\n", instanceId);
			}
			virDomainFree(dom); /* necessary? */
		} else {
			if (instance->state != BOOTING)
				logprintfl (EUCAWARN, "warning: domain %s to be terminated not running on hypervisor\n", instanceId);
		}
	} 

	/* change the state and let the monitoring_thread clean up state */
	change_state (instance, SHUTOFF);
	*previousState = instance->stateCode;
	*shutdownState = instance->stateCode;

	return OK;
}

static int
doDescribeInstances(	ncMetadata *meta,
			char **instIds,
			int instIdsLen,
			ncInstance ***outInsts,
			int *outInstsLen)
{
	ncInstance *instance;
	int total, i, j, k;

	*outInstsLen = 0;
	*outInsts = NULL;

	sem_p (inst_sem);
	if (instIdsLen == 0) { /* describe all instances */
		total = total_instances (&global_instances);
	} else {
		total = instIdsLen;
	}
	*outInsts = malloc(sizeof(ncInstance *)*total);
	if ((*outInsts) == NULL) {
		sem_v (inst_sem);
		return OUT_OF_MEMORY;
	}

	k = 0;
	for (i=0; (instance = get_instance(&global_instances)) != NULL; i++) {
		/* only pick ones the user (or admin)  is allowed to see */
		if (strcmp(meta->userId, nc->admin_user_id) 
				&& strcmp(meta->userId, instance->userId))
			continue;

		if (instIdsLen > 0) {
			for (j=0; j < instIdsLen; j++)
				if (!strcmp(instance->instanceId, instIds[j]))
					break;

			if (j >= instIdsLen)
				/* instance of not relavance right now */
				continue;
		}

		(* outInsts)[k++] = instance;
	}
	*outInstsLen = k;
	sem_v (inst_sem);

	return OK;
}

static int
doDescribeResource(	ncMetadata *meta,
			char *resourceType,
			ncResource **outRes)
{
    ncResource * res;
    ncInstance * inst;

    /* stats to re-calculate now */
    long long mem_free;
    long long disk_free;
    int cores_free;

    /* intermediate sums */
    long long sum_mem = 0;  /* for known domains: sum of requested memory */
    long long sum_disk = 0; /* for known domains: sum of requested disk sizes */
    int sum_cores = 0;      /* for known domains: sum of requested cores */


    *outRes = NULL;
    sem_p (inst_sem); 
    while ((inst=get_instance(&global_instances))!=NULL) {
        if (inst->state == TEARDOWN) continue; /* they don't take up resources */
        sum_mem += inst->params.memorySize;
        sum_disk += (inst->params.diskSize + SWAP_SIZE);
        sum_cores += inst->params.numberOfCores;
    }
    sem_v (inst_sem);
    
    disk_free = nc->disk_max - sum_disk;
    if ( disk_free < 0 ) disk_free = 0; /* should not happen */
    
    mem_free = nc->mem_max - sum_mem;
    if ( mem_free < 0 ) mem_free = 0; /* should not happen */

    cores_free = nc->cores_max - sum_cores; /* TODO: should we -1 for dom0? */
    if ( cores_free < 0 ) cores_free = 0; /* due to timesharing */

    /* check for potential overflow - should not happen */
    if (nc->mem_max > INT_MAX ||
        mem_free > INT_MAX ||
        nc->disk_max > INT_MAX ||
        disk_free > INT_MAX) {
        logprintfl (EUCAERROR, "stats integer overflow error (bump up the units?)\n");
        logprintfl (EUCAERROR, "   memory: max=%-10lld free=%-10lld\n", nc->mem_max, mem_free);
        logprintfl (EUCAERROR, "     disk: max=%-10lld free=%-10lld\n", nc->disk_max, disk_free);
        logprintfl (EUCAERROR, "    cores: max=%-10d free=%-10d\n", nc->cores_max, cores_free);
        logprintfl (EUCAERROR, "       INT_MAX=%-10d\n", INT_MAX);
        return 10;
    }
    
    res = allocate_resource ("OK", nc->mem_max, mem_free, nc->disk_max, disk_free, nc->cores_max, cores_free, "none");
    if (res == NULL) {
        logprintfl (EUCAERROR, "Out of memory\n");
        return 1;
    }
    * outRes = res;

    return OK;
}

static int
doPowerDown(ncMetadata *ccMeta)
{
	char cmd[1024];
	int rc;

	logprintfl(EUCADEBUG, "PowerOff called\n");
	snprintf(cmd, 1024, "%s /etc/init.d/powernap now", nc->rootwrap_cmd_path);
	logprintfl(EUCADEBUG, "saving power: %s\n", cmd);
	rc = system(cmd);
	rc = rc>>8;
	if (rc)
		logprintfl(EUCAERROR, "cmd failed: %d\n", rc);
  
	logprintfl(EUCADEBUG, "PowerOff done\n");

	return OK;
}

static int
doStartNetwork(	vnetConfig *vnetconfig,
		ncMetadata *ccMeta, 
		char **remoteHosts, 
		int remoteHostsLen, 
		int port, 
		int vlan) {
	int rc, ret, i, status;
	char *brname;

	logprintfl (EUCAINFO, "StartNetwork(): called\n");

	rc = vnetStartNetwork(vnetconfig, vlan, NULL, NULL, &brname);
	if (rc) {
		ret = 1;
		logprintfl (EUCAERROR, "StartNetwork(): ERROR return from vnetStartNetwork %d\n", rc);
	} else {
		ret = 0;
		logprintfl (EUCAINFO, "StartNetwork(): SUCCESS return from vnetStartNetwork %d\n", rc);
	}
	logprintfl (EUCAINFO, "StartNetwork(): done\n");

	return (ret);
}

static int
doAttachVolume(	ncMetadata *meta,
		char *instanceId,
		char *volumeId,
		char *remoteDev,
		char *localDev)
{
	return ERROR_FATAL;
}

static int
doDetachVolume(	ncMetadata *meta,
		char *instanceId,
		char *volumeId,
		char *remoteDev,
		char *localDev,
		int force)
{
	return ERROR_FATAL;
}

struct handlers default_libvirt_handlers = {
    .name = "default",
    .doInitialize        = doInitialize,
    .doDescribeInstances = doDescribeInstances,
    .doRunInstance       = doRunInstance,
    .doTerminateInstance = doTerminateInstance,
    .doRebootInstance    = doRebootInstance,
    .doGetConsoleOutput  = doGetConsoleOutput,
    .doDescribeResource  = doDescribeResource,
    .doStartNetwork      = doStartNetwork,
    .doPowerDown         = doPowerDown,
    .doAttachVolume      = doAttachVolume,
    .doDetachVolume      = doDetachVolume
};
