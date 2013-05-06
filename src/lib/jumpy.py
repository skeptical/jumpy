import sys, os, inspect

# ensure that packages installed here (jumpy/lib) have precedence
here = os.path.dirname(inspect.getfile(inspect.currentframe()))
if here in sys.path:
	sys.path.remove(here)
sys.path.insert(1, here)

import imp, traceback, __builtin__
from py4j.java_gateway import GatewayClient, JavaGateway
import vmsg, imgfx

try: pms
except NameError:
	host, port = os.environ['JGATEWAY'].split(':')
	hascb, cbport = (True, int(os.getenv('JCLIENT'))) if 'JCLIENT' in os.environ else (False, None)
	client = GatewayClient(address=host, port=int(port))
	gateway = JavaGateway(client, start_callback_server=hascb, python_proxy_port=cbport)
	__builtin__.pms = gateway.entry_point
	__builtin__.pms._addItem = pms.addItem
	__builtin__.pms._addPath = pms.addPath
	__builtin__.pms._setEnv = pms.setEnv
	__builtin__.pms._addPlayer = pms.addPlayer
	# constants from net.pms.formats.Format:
	__builtin__.PMS_AUDIO = 1
	__builtin__.PMS_IMAGE = 2
	__builtin__.PMS_VIDEO = 4
	__builtin__.PMS_UNKNOWN = 8
	__builtin__.PMS_PLAYLIST = 16
	__builtin__.PMS_ISO = 32
	__builtin__.PMS_CUSTOM = 64
	# new constants:
	__builtin__.PMS_MEDIA = 1025
	__builtin__.PMS_FOLDER = 1026
	__builtin__.PMS_ACTION = 1028
	__builtin__.PMS_UNRESOLVED = 2048
	__builtin__.PMS_FEED = 4096
	__builtin__.PMS_AUDIOFEED = 4097
	__builtin__.PMS_IMAGEFEED = 4098
	__builtin__.PMS_VIDEOFEED = 4100
	# util() constants
	__builtin__.PMS_VERSION = 1
	__builtin__.PMS_HOME = 2
	__builtin__.PMS_PROFILEDIR = 3
	__builtin__.PMS_LOGDIR = 4
	__builtin__.PMS_PLUGINJAR = 5
	__builtin__.PMS_RESTART = 6
	__builtin__.PMS_FOLDERNAME = 7
	__builtin__.PMS_GETPROPERTY = 8
	__builtin__.PMS_SETPROPERTY = 9
	__builtin__.PMS_SETPMS = 10
	__builtin__.PMS_REBOOT = 11
	__builtin__.PMS_XMBPATH = 12
	__builtin__.PMS_ICON = 13
	__builtin__.PMS_RESOURCE = 14
	__builtin__.PMS_REFRESH = 15
	__builtin__.PMS_RUN = 16
	__builtin__.PMS_SUBTITLE = 17
	__builtin__.PMS_GETVAR = 18
	__builtin__.PMS_LOG = 19
	# constants from net.pms.encoders.Player
	__builtin__.PMS_VIDEO_SIMPLEFILE_PLAYER = 0
	__builtin__.PMS_AUDIO_SIMPLEFILE_PLAYER = 1
	__builtin__.PMS_VIDEO_WEBSTREAM_PLAYER = 2
	__builtin__.PMS_AUDIO_WEBSTREAM_PLAYER = 3
	__builtin__.PMS_MISC_PLAYER = 4
	__builtin__.PMS_NATIVE = "NATIVE"

def flatten(list):
	a = []
	for item in list:
		a.append(item.replace(' , ',' ,, '))
	return '[%s]' % ' , '.join(a)

def decode(s):
	if isinstance(s, str):
		for encoding in ['utf-8', 'ISO-8859-1', 'windows-1252']: # etc...
			try: return s.decode(encoding)
			except: pass
	return s

def pms_addItem(itemtype, name, argv, thumb = None, data = None):
	if type(argv).__name__ == 'list':
		argv = flatten(argv)
	pms._addItem(itemtype, decode(name), decode(argv), decode(thumb), decode(data))

# convenience wrappers
def pms_addFolder(name, cmd, thumb=None):
	pms_addItem(PMS_FOLDER, name, cmd, thumb)

def pms_addAudio(name, cmd, thumb=None):
	pms_addItem(PMS_AUDIO, name, cmd, thumb)

def pms_addImage(name, cmd, thumb=None):
	pms_addItem(PMS_IMAGE, name, cmd, thumb)

def pms_addVideo(name, cmd, thumb=None):
	pms_addItem(PMS_VIDEO, name, cmd, thumb)

def pms_addPlaylist(name, cmd, thumb=None):
	pms_addItem(PMS_PLAYLIST, name, cmd, thumb)

def pms_addISO(name, cmd, thumb=None):
	pms_addItem(PMS_ISO, name, cmd, thumb)

def pms_addAudiofeed(name, cmd, thumb=None):
	pms_addItem(PMS_AUDIOFEED, name, cmd, thumb)

def pms_addImagefeed(name, cmd, thumb=None):
	pms_addItem(PMS_IMAGEFEED, name, cmd, thumb)

def pms_addVideofeed(name, cmd, thumb=None):
	pms_addItem(PMS_VIDEOFEED, name, cmd, thumb)

def pms_addAction(name, cmd, thumb=None, playback=None):
	pms_addItem(PMS_ACTION, name, cmd, thumb, playback)

def pms_addCmd(name, cmd, thumb=None, ok='Success', fail='Failed'):
	pms_addItem(PMS_ACTION, name, cmd, thumb, '+CMD : %s : %s' % (ok, fail))

def pms_addConsoleCmd(name, cmd, thumb=None):
	pms_addItem(PMS_ACTION, name, cmd, '#console' if thumb is None else thumb, '+CMDCONSOLE')

def pms_addMedia(name, format, cmd, thumb=None):
	pms_addItem(PMS_MEDIA, name, cmd, thumb, format)

def pms_addUnresolved(name, cmd, thumb=None, type=PMS_VIDEO):
	pms_addItem(type|PMS_UNRESOLVED, name, cmd, thumb, None)

def pms_submit(name, uri, thumb=None):
	pms_addItem(PMS_MEDIA, name, uri, thumb, None)

def pms_addPath(path=None):
	pms._addPath(path)

def pms_setPath(path=None): # deprecated
	pms._addPath(path)

def pms_setEnv(name=None, val=None):
	pms._setEnv(name, val)

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

def pms_refresh(up=0):
	return pms_util(PMS_REFRESH, str(up))

def pms_restart():
	return pms_util(PMS_RESTART)

def pms_reboot():
	return pms_util(PMS_REBOOT)

def pms_run(cmd):
	if type(cmd).__name__ == 'list':
		cmd = flatten(cmd)
	print 'cmd=%s'%cmd
	return int(pms_util(PMS_RUN, cmd))

def pms_getFolderName():
	return pms_util(PMS_FOLDERNAME)

def pms_getXmbPath():
	return pms_util(PMS_XMBPATH)

def pms_getVar(key):
	return pms_util(PMS_GETVAR, key)

def pms_getProperty(key):
	return pms_util(PMS_GETPROPERTY, key)

def pms_setProperty(key, val):
	pms_util(PMS_SETPROPERTY, key, val)

def pms_getResource(src):
	return pms_util(PMS_RESOURCE, src)

def pms_setIcon(fmt, img):
	pms_util(PMS_ICON, fmt, img)

def pms_setSubtitles(path):
	pms_util(PMS_SUBTITLE, path)

def pms_log(msg):
	pms_util(PMS_LOG, msg)

def pms_addPlayer(name, cmd, supported, mediatype=PMS_VIDEO, purpose=PMS_MISC_PLAYER, desc=None, icon=None, playback=None):
	if type(cmd).__name__ == 'list':
		cmd = flatten(cmd)
	pms._addPlayer(name, cmd, supported, mediatype, purpose, desc, icon, playback)

__builtin__.pms.addFolder = pms_addFolder
__builtin__.pms.addAudio = pms_addAudio
__builtin__.pms.addImage = pms_addImage
__builtin__.pms.addVideo = pms_addVideo
__builtin__.pms.addPlaylist = pms_addPlaylist
__builtin__.pms.addISO = pms_addISO
__builtin__.pms.addAudiofeed = pms_addAudiofeed
__builtin__.pms.addImagefeed = pms_addImagefeed
__builtin__.pms.addVideofeed = pms_addVideofeed
__builtin__.pms.addAction = pms_addAction
__builtin__.pms.addCmd = pms_addCmd
__builtin__.pms.addConsoleCmd = pms_addConsoleCmd
__builtin__.pms.addMedia = pms_addMedia
__builtin__.pms.addUnresolved = pms_addUnresolved
__builtin__.pms.submit = pms_submit
__builtin__.pms.addItem = pms_addItem
__builtin__.pms.addPath = pms_addPath
__builtin__.pms.setPath = pms_addPath # deprecated
__builtin__.pms.setEnv = pms_setEnv
__builtin__.pms.version = pms_version
__builtin__.pms.getHome = pms_getHome
__builtin__.pms.getProfileDir = pms_getProfileDir
__builtin__.pms.getLogDir = pms_getLogDir
__builtin__.pms.getPluginJar = pms_getPluginJar
__builtin__.pms.refresh = pms_refresh
__builtin__.pms.restart = pms_restart
__builtin__.pms.reboot = pms_reboot
__builtin__.pms.run = pms_run
__builtin__.pms.getFolderName = pms_getFolderName
__builtin__.pms.getXmbPath = pms_getXmbPath
__builtin__.pms.getVar = pms_getVar
__builtin__.pms.getProperty = pms_getProperty
__builtin__.pms.setProperty = pms_setProperty
__builtin__.pms.getResource = pms_getResource
__builtin__.pms.setIcon = pms_setIcon
__builtin__.pms.setSubtitles = pms_setSubtitles
__builtin__.pms.log = pms_log
__builtin__.pms.addPlayer = pms_addPlayer

lib = os.path.dirname(os.path.realpath(__file__))
resources = os.path.join(lib, 'resources')

def pms_imgfx(imgfile, fx, dir=None):
	imgfx.render(imgfile, fx=fx, outdir=dir)

def pms_vmsg(**kwargs):
	if 'img' in kwargs:
		kwargs['img'] = pms_getResource(kwargs['img'])
	if not 'out' in kwargs:
		kwargs['out'] = os.environ['OUTFILE']
	vmsg.vmsg(**kwargs)

def pms_info(msg, seconds=7):
	pms_vmsg(msg=msg, seconds=seconds, fill='white', background='#3465a4', pointsize=20,
		img='#info+t=0,0_.675,.75_0;c=160x184-26-60;s;3', imggrav='north')

def pms_ok(msg, seconds=7):
	pms_vmsg(msg=msg, seconds=seconds, fill='white', background='#3465a4', pointsize=20,
		img='#checkmark+f=#99ff00;c=160x184-0-24;s;3', imggrav='north')

def pms_warn(msg, seconds=7):
	pms_vmsg(msg=msg, seconds=seconds, fill='white', background='#3465a4', pointsize=20,
		img='#warning+f=orange;t=0,0_.675,.75_0;c=160x184-26-60;s;3', imggrav='north')

def pms_err(msg, seconds=7):
	pms_vmsg(msg=msg, seconds=seconds, fill='white', background='#3465a4', pointsize=20,
		img='#x+f=#ff3300;c=160x184-0-24;s;3', imggrav='north')

__builtin__.pms.imgfx = pms_imgfx
__builtin__.pms.vmsg = pms_vmsg
__builtin__.pms.info = pms_info
__builtin__.pms.ok = pms_ok
__builtin__.pms.warn = pms_warn
__builtin__.pms.err = pms_err

# flush regularly to stay in sync with java output
class flushed(object):
	def __init__(self, s):
		self.s = s
	def write(self, x):
		try: self.s.write(x)
		except UnicodeEncodeError: self.s.write(x.encode('ascii', 'ignore'))
		self.s.flush()
sys.stdout = flushed(sys.stdout)
#sys.stderr = flushed(sys.stderr)

__builtin__.sys = sys


if __name__ == "__main__" and len(sys.argv) == 1:

	if sys.platform.startswith('win32'):
		try:
			# reset %pms% to short paths so we don't get into trouble with cmd /c
			from ctypes import windll, create_unicode_buffer, sizeof
			buf = create_unicode_buffer(512)
			windll.kernel32.GetShortPathNameW(u'%s' % sys.executable, buf, sizeof(buf))
			py = buf.value
			windll.kernel32.GetShortPathNameW(u'%s' % sys.argv[0], buf, sizeof(buf))
			pms_util(PMS_SETPMS, '%s %s' % (py, buf.value))

			# in Windows 'convert.exe' has a name conflict with a system utility
			# (ntfs partition converter) and python's underlying CreateProcess()
			# invocation doesn't seem to search the current %path% to resolve
			# the executable
			for path in os.environ.get('Path').split(os.path.pathsep):
				convert = os.path.join(path, 'convert.exe')
				if os.path.exists(convert):
					windll.kernel32.GetShortPathNameW(u'%s' % convert, buf, sizeof(buf))
					pms_setEnv('imconvert', buf.value)
					break;
		except:
			traceback.print_exc(file=sys.stderr)

	sys.stderr.write("\npython %s.%s.%s\n" % (sys.version_info.major, sys.version_info.minor, sys.version_info.micro))

elif __name__ == "__main__":

	# we're running via system call:
	# interpret the args as a function call and see what happens

	args = []
	named = sys.argv[1] == 'vmsg'

	for arg in sys.argv[2:]:
		try:
			# filter named args, constants or numbers
			named or arg[0:4] == "PMS_" or float(arg)
			args.append(arg)
		except:
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

