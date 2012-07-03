import os, os.path, sys, imp, traceback
import __builtin__
from py4j.java_gateway import GatewayClient, JavaGateway

try: pms
except NameError:
	host, port = os.environ['JGATEWAY'].split(':')
	client = GatewayClient(address=host, port=int(port))
	gateway = JavaGateway(client)
	__builtin__.pms = gateway.entry_point
#	__builtin__.pms = JavaGateway(client).entry_point
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
	__builtin__.PMS_FOLDERNAME = 7;
	__builtin__.PMS_GETPROPERTY = 8;
	__builtin__.PMS_SETPROPERTY = 9;

# wrapper to flatten the argv list into a tokenized string
def pms_addItem(t, name, argv, thumb = None):
	if type(argv).__name__ == 'list':
		a = []
		for arg in argv:
			a.append(arg.replace(' , ',' ,, '))
		argv = '[%s]' % ' , '.join(a)
	pms._addItem(t, name, argv, thumb)

# convenience wrappers
def pms_util(action, arg1=None, arg2=None):
	return pms.util(action, arg1, arg2)

# 'varargs' version to use with 'String...' on java side:
#def pms_util(action, *args):
#	argarray = JavaGateway(client).new_array(gateway.jvm.java.lang.String, len(args))
#	for i in range(len(args)):
#		argarray[i] = args[i]
#	return pms.util(action, argarray)

def pms_version():
	return pms_util(PMS_VERSION)

def pms_getHome():
	return pms_util(PMS_HOME)

def pms_getProfileDir():
	return pms_util(PMS_PROFILEDIR)

def pms_getLogDir():
	return pms_util(PMS_LOGDIR)

def pms_getPluginJar():
	return pms_util(PMS_PLUGINJAR)

def pms_restart():
	return pms_util(PMS_RESTART)

def pms_getFolderName():
	return pms_util(PMS_FOLDERNAME)

def pms_getProperty(key):
	return pms_util(PMS_GETPROPERTY, key)

def pms_setProperty(key, val):
	pms_util(PMS_SETPROPERTY, key, val)

def pms_setEnv(name, val):
	pms.setEnv(name, val)

__builtin__.pms.addItem = pms_addItem
__builtin__.pms.version = pms_version
__builtin__.pms.getHome = pms_getHome
__builtin__.pms.getProfileDir = pms_getProfileDir
__builtin__.pms.getLogDir = pms_getLogDir
__builtin__.pms.getPluginJar = pms_getPluginJar
__builtin__.pms.restart = pms_restart
__builtin__.pms.getFolderName = pms_getFolderName
__builtin__.pms.getProperty = pms_getProperty
__builtin__.pms.setProperty = pms_setProperty

# flush regularly to stay in sync with java output
class flushed(object):
	 def __init__(self, s):
		  self.s = s
	 def write(self, x):
		  self.s.write(x)
		  self.s.flush()
sys.stdout = flushed(sys.stdout)

__builtin__.sys = sys


if __name__ == "__main__" and len(sys.argv) == 1:
	sys.stderr.write("python %s.%s.%s\n" % (sys.version_info.major, sys.version_info.minor, sys.version_info.micro))

elif __name__ == "__main__":

	# we're running via system call:
	# interpret the args as a function call and see what happens

	args = []
	for arg in sys.argv[2:]:
		# check if it's a constant
		if arg[0:4] == "PMS_":
			try:
				args.append(str(eval(arg)))
				continue
			except: pass
		# it must be a string
		args.append(repr(arg))

	code = 'output=pms_%s(%s)' % (sys.argv[1], ','.join(args))
	sys.stderr.write("calling pms.%s\n" % code[11:])

	try:
		exec code
		if output: print output
	except:
		traceback.print_exc(file=sys.stderr)
		sys.stderr.write("Error: invalid syntax.\n")
		sys.exit(-1)


