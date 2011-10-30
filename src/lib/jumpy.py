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

# wrapper to flatten the argv list into a tokenized string
def addItem(t, name, argv, thumb):
	if type(argv).__name__ == 'list':
		a = []
		for arg in argv:
			a.append(arg.replace('|','||'))
		argv = ' | '.join(a)
	pms._addItem(t, name, argv, thumb)

__builtin__.pms.addItem = addItem

