import os, os.path, sys, imp
import __builtin__
from py4j.java_gateway import GatewayClient, JavaGateway

try: pms
except NameError:
	host, port = os.environ['JGATEWAY'].split(':')
	client = GatewayClient(address=host, port=int(port))
	__builtin__.pms = JavaGateway(client).entry_point
	__builtin__.pms._addItem = pms.addItem
	# new constants:
	__builtin__.PMS_UNRESOLVED = -2
	__builtin__.PMS_FOLDER = -1
	# constants from net.pms.formats.Format:
	__builtin__.PMS_AUDIO = 1
	__builtin__.PMS_IMAGE = 2
	__builtin__.PMS_VIDEO = 4
	__builtin__.PMS_UNKNOWN = 8
	__builtin__.PMS_PLAYLIST = 16
	__builtin__.PMS_ISO = 32
	# new constants (net.pms.formats.Format|FEED) where FEED=4096
	__builtin__.PMS_AUDIOFEED = 4097
	__builtin__.PMS_IMAGEFEED = 4098
	__builtin__.PMS_VIDEOFEED = 4100
	# util() constants
	__builtin__.PMS_VERSION = 1;
	__builtin__.PMS_HOME = 2;
	__builtin__.PMS_PROFILEDIR = 3;
	__builtin__.PMS_LOGDIR = 4;
	__builtin__.PMS_PLUGINJAR = 5;
	__builtin__.PMS_RESTART = 6;

# wrapper to flatten the argv list into a tokenized string
def pms_addItem(t, name, argv, thumb):
	if type(argv).__name__ == 'list':
		a = []
		for arg in argv:
			a.append(arg.replace('|','||'))
		argv = ' | '.join(a)
	pms._addItem(t, name, argv, thumb)

# convenience wrappers
def pms_util(action, data = None):
	return pms.util(action, data)

def pms_version():
	return pms.util(PMS_VERSION, None)

def pms_getHome():
	return pms.util(PMS_HOME, None)

def pms_getProfileDir():
	return pms.util(PMS_PROFILEDIR, None)

def pms_getLogDir():
	return pms.util(PMS_LOGDIR, None)

def pms_getPluginJar():
	return pms.util(PMS_PLUGINJAR, None)

def pms_restart():
	return pms.util(PMS_RESTART, None)

__builtin__.pms.addItem = pms_addItem
__builtin__.pms.version = pms_version
__builtin__.pms.getHome = pms_getHome
__builtin__.pms.getProfileDir = pms_getProfileDir
__builtin__.pms.getLogDir = pms_getLogDir
__builtin__.pms.getPluginJar = pms_getPluginJar
__builtin__.pms.restart = pms_restart

# flush regularly to stay in sync with java output
class flushed(object):
    def __init__(self, s):
        self.s = s
    def write(self, x):
        self.s.write(x)
        self.s.flush()
sys.stdout = flushed(sys.stdout)

__builtin__.sys = sys

