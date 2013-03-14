import os, sys, traceback, __builtin__
import jumpy

class resolver:

	class Java:
		implements = ['net.pms.external.infidel.jumpy.jumpy$urlResolver']

	def resolve(self, url):
#		print 'resolving %s' % url
		if not 'plugin://' in url:
			url = urlresolver.resolve(url)
		elif url.startswith('['):
			argv = url[1:-1].split(' , ')
			if len(argv) > 1 and argv[1].startswith('plugin://'):
				url = argv[1]
		if url and url.startswith('plugin://'):
			self.item = None
			xbmcinit.run_addon(url)
			url = self.item
		if url:
			pms.log('xbmc-resolver: %s' % url)
			return url.strip()
		return None

	def addItem(self, itemtype, name, argv, thumb = None, data = None):
#		print 'resolver.addItem: %s' % argv
		self.item = argv

	def __init__(self):
		try:
			pms.register(self)
		except: traceback.print_exc()
		__builtin__.pms.addItem = self.addItem


xbmclib = os.path.join(pms.getHome(), 'xbmc')
if os.path.exists(xbmclib):
	sys.path.append(xbmclib)
else:
	print 'Error: jumpy-xbmc module not found.'
	sys.exit(1)

import xbmcinit

addondir = os.path.join(_special['home'], 'addons', 'script.module.urlresolver')
if not os.path.exists(addondir):
	print 'Error: script.module.urlresolver not found.'
	sys.exit(1)
id = xbmcinit.read_addon(dir=addondir, full=True)
sys.path.extend(_info[id]['_pythonpath'].split(os.path.pathsep))
sys.argv = [sys.argv[0], '?']

import urlresolver
resolver()

