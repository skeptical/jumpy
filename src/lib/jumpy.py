import os, os.path, sys, imp
import __builtin__
from py4j.java_gateway import GatewayClient, JavaGateway

try: pms
except NameError:
	host, port = os.environ['JGATEWAY'].split(':')
	client = GatewayClient(address=host, port=int(port))
	__builtin__.pms = JavaGateway(client).entry_point
	# constants from net.pms.formats.Format:
	__builtin__.PMS_ISO = 32
	__builtin__.PMS_PLAYLIST = 16
	__builtin__.PMS_UNKNOWN = 8
	__builtin__.PMS_VIDEO = 4
	__builtin__.PMS_AUDIO = 1
	__builtin__.PMS_IMAGE = 2

# http://pyunit.sourceforge.net/notes/reloading.html
class RollbackImporter:
	def __init__(self):
		"Creates an instance and installs as the global importer"
		self.previousModules = sys.modules.copy()
		self.realImport = __builtin__.__import__
		__builtin__.__import__ = self._import
		self.newModules = {}
		
	def _import(self, name, globals=None, locals=None, fromlist=[]):
		result = apply(self.realImport, (name, globals, locals, fromlist))
		self.newModules[name] = 1
		return result
		
	def uninstall(self):
		for modname in self.newModules.keys():
			if not self.previousModules.has_key(modname):
				# Force reload when modname next imported
				del(sys.modules[modname])
		__builtin__.__import__ = self.realImport

#rollbackImporter = RollbackImporter()

def reset1():
	global rollbackImporter
	if rollbackImporter:
		rollbackImporter.uninstall()
	rollbackImporter = RollbackImporter()

def reset(basemods):
	"""Unload any new modules loaded by a previous run."""
	print "Clearing plugin modules"
	for module in sys.modules.keys():
		if not module in basemods:
			print "  ",module
			del(sys.modules[module])

def run(argv, basemods=None):
	if basemods is not None:
		reset(basemods)
	dir,file = os.path.split(argv[0])
	module = os.path.splitext(file)[0]
	os.chdir(dir)
	dir = os.getcwd()
	sys.path.append(dir)
	sys.argv = []
	sys.argv.append(os.path.join(dir,file))
	for arg in argv[1:]:
		sys.argv.append(arg)
	print "cwd=", dir
	print "module=", module
	print "running:",' '.join(sys.argv)
	__import__(module)
#	imp.load_source(module, argv[0])


if __name__ == "__main__":
	run(sys.argv[1:])
