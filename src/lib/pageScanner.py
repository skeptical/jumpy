import os, sys, re, urllib2, traceback
from urlparse import urlparse, urljoin, parse_qs
from bs4 import BeautifulSoup
import jumpy
sys.path.append(os.path.abspath(os.path.join('..', 'webreader'))) # FIXME


def isRelativeFolderPath(url):
	return url.endswith('/') and not '://' in url

def ismedia(mime):
	return mime.startswith('video/') or mime.startswith('audio/') or mime.startswith('image/')

def istext(mime):
	return 'text' in mime or 'xml' in mime or 'json' in mime or 'charset' in mime.lower()

class ResultFound(Exception):
	pass

class scanner:

	FULL = 1
	SINGLE = 2
	FOLDERS = 4
	UPDATEXMB = 8

	def __init__(self, resolver, mode=FULL, timeout=10):
		self.resolver = resolver
		self.mode = mode or scanner.FULL
		self.timeout = float(timeout or 10)
		self.items = {}
		self.media = re.compile(r'([a-z]+://.+?\.(3g2|3gp|asf|avi|divx|drc|f4a|f4b|f4p|f4v|flac|flv|gif|gifv|m2v|m4p|m4v|mkv|mng|mov|mp2|mp3|mp4|mpe|mpeg|mpg|mpv|mxf|nsv|ogg|ogv|qt|rm|rmvb|roq|speex|svi|ts|vob|wav|webm|wma|wmv|yuv))')

	def scan(self, url):
		self.items = {}
		try:
			self.scrape(url)
		except ResultFound, e:
			return e.args[0]
		except:
			pms.log(traceback.format_exc())
		return None

	def scrape(self, url, depth=1):
		print 'scanning: %s' % url

		prev = len(self.items)

		# if the url itself generates a hit we're done
		self.check(url, url)
		if len(self.items) > prev:
			return

#		import requests
#		req = requests.head(url, allow_redirects=False)
#		info = req.headers

		# send a head request to see the content-type
		req = urllib2.Request(url)
		req.get_method = lambda : 'HEAD'
		info = urllib2.urlopen(req, timeout=self.timeout).info()

		if info:
			print '\nHEAD:\n%s' % info
			content = info.get('content-type')
			if content:
				if ismedia(content):
					# found it
					self.add(url)
					return
				elif not istext(content):
					print 'content is "%s", skipping...' % content
					return

		print 'scraping: %s' % url

		# it's (hopefully) text, fetch at most 100k
		req = urllib2.Request(url)
		req.headers['Range'] = 'bytes=0-102399'
		req = urllib2.urlopen(req, timeout=self.timeout)
		src = req.read(102400)

		if not src:
			print 'error downloading content'
			return

		# it could be rss
		if src.startswith('<?xml'):
			try:
				import feedparser
				rss = feedparser.parse(src)
				if 'media' in rss.namespaces:
					print 'found %s rss items' % len(rss['items'])
					for item in rss['items']:
#						print item
						thumb = item['thumbnail'] if 'thumbnail' in item else None
						# if no thumbnail, resolver may have better luck
						if not thumb and self.check(item['link'], item['link'], item['title']):
							continue
						else:
							self.add(item['link'], pms.esc(item['title']), thumb)
			except: pass
			# if we have any new items we're done
			if len(self.items) > prev:
				return

		# treat it as html
		soup = BeautifulSoup(src)
		ct = 0

		# determine base url
		try: base = soup('base')[0]['href']
		except: base = url

		# check for urls embedded in script blocks
		for script in [tag.extract().string for tag in soup('script')]:
#			print 'script=%s'%script
			if script:
				ct += 1
				for u,ext in self.media.findall(script):
					self.add(u)

		# check 'source' tags
		for tag in [tag.extract() for tag in soup('source')]:
#			print 'source=%s'%source
			ct += 1
			self.check(base, tag['src'])

		# check flashvars in 'embed' tags
		# see http://helpx.adobe.com/flash/kb/pass-variables-swfs-flashvars.html
		for fv in [parse_qs(tag['flashvars']) for tag in soup.findAll('embed', flashvars=True)]:
			if 'shareUrl' in fv:
				for u in fv['shareUrl']:
#					print '\nshareUrl=%s\n'%u
					ct += 1
					self.check(base, u)

		# remove any iframes
		iframes = [tag.extract()['src'] for tag in soup('iframe') if 'src' in tag.attrs] \
			if depth < 2 else []

		# and check remaining likely link locations
		urls = [tag['src'] for tag in soup.findAll(src=True)]
		urls += [tag['href'] for tag in soup.findAll(href=True)]
#		urls += [tag['content'] for tag in soup.findAll(content=True)]
		urls += [u for u in [tag['content'] for tag in soup.findAll(content=True)] \
			if '/' in u or '.' in u]
		print 'scanning %s urls' % (ct + len(urls))
		for u in urls:
			self.check(base, u)

		# recurse into iframes
		for u in iframes:
			self.scrape(urljoin(base, u), depth + 1)

	def check(self, base, url, name=None, thumb=None, sub=None, details=None):
		u = urljoin(base, url)
		if self.media.search(u):
			self.add(u, name, thumb)
		elif self.mode & scanner.FOLDERS and isRelativeFolderPath(url):
			self.add(u, url[:-1], thumb, isfolder=True)
		elif self.resolver._resolve(u):
			r = self.resolver
			self.add(r.url, r.name if r.name else name, r.thumb if r.thumb else thumb, r.sub if r.sub else sub,
				r.details if r.details else details)
		else:
			return False
		return True

	def add(self, url, name=None, thumb=None, sub=None, details=None, isfolder=False):
		label = name if name else os.path.basename(urlparse(url)[2])
		self.items[url] = (label, thumb, isfolder)
		if self.mode & scanner.UPDATEXMB:
			if isfolder:
				pms.addFolder(pms.esc(label), [sys.argv[0], url], thumb)
			else:
				pms.addVideo(pms.esc(label), url, thumb, details)
				if sub:
					pms.setSubtitles(sub)
		if self.mode & scanner.SINGLE and not isfolder:
			raise ResultFound({'uri': pms.decode(url), 'name': pms.decode(name), 'thumb': pms.decode(thumb), 'sub': pms.decode(sub),
					'details': pms.stringify(details) if details else None})

	def getitems(self):
		return self.items.items()

