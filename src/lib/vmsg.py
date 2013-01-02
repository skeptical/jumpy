#!/bin/python

import sys, os, tempfile
from subprocess import Popen, PIPE

class vmsg:
	def __init__(
				self,
				seconds = 10,
				msg = "",
				file = None,
				out = '-',
				countdown = False,
				fill = 'white',
				background = 'black', # '#3465a4'
				pointsize = 20,
				font = "DejaVu-Sans-Mono-Book", #"arial",
				density = 75,
				gravity = None,
				rate = '1',
				img = None,
				imggrav = 'center',
				resize = False,
				#TODO: size = 'SD'/'HD'
			):

		size = '720x480'
		target = 'ntsc-dvd'

		filemode = False if file == None else True
		tmp = ''
		if filemode:
			try:
				msg = open(file).read()
			except:
				try:
					from urllib2 import urlopen
					msg = urlopen(file).read()
#					f = urlopen(file)
#					if f.headers['content-type'].startswith('text'):
#						msg = f.read()
				except:
					msg = file
			tmp = os.path.join(tempfile.gettempdir(), 'tmp.txt')
			open(tmp, 'w').write(msg.expandtabs(3).replace('\\n', '\n'))
			img = None
		else:
			msg = msg.replace('\n', '\\n')

		sys.stderr.write('mode: %s\n' % ('file' if filemode else 'caption'))

		ffmpeg = [
			'ffmpeg', '-f', 'image2pipe', '-vcodec', 'ppm', '-r', str(rate),
			 '-i', '-', '-target', target, '-aspect', '16:9', '-an', '-y',
			'-y' if filemode else '-t', '-y' if filemode else str(seconds - 1),
			out
		]

		imconvert = os.getenv('imconvert') if 'imconvert' in os.environ else 'convert'
		oparen, cparen = ('(',')')  if sys.platform.startswith('win32') else ('\(','\)')

		# see http://www.imagemagick.org/Usage/text/
		# using '-verbose' as a dummy argument

		convert = [
			imconvert,
			'-page' if filemode else '-size', size,
			'-verbose' if filemode else 'canvas:%s' % background,
			'-background', '"%s"' % background if filemode else 'none'
		]

		if not img == None:
			convert.extend(['"%s"' % img, '-gravity', imggrav])
			if resize:
				convert.extend(['-resize', size])
			convert.append('-composite')

		convert.extend([
			'-gravity', 'none' if filemode else 'center' if gravity == None else gravity,
			'-density', str(density),
			'-font', font, '-pointsize', str(pointsize), '-fill', '"%s"' % fill,
			'+insert' # insert a throwaway initial frame for ffmpeg
		])

		labelindex = len(convert)-1

		for i in range(1, 2 if filemode else seconds):
			if not filemode:
				caption = 'caption:"%s"' % (msg)
				if countdown:
					t = seconds - i
					caption = 'caption:"%s %d:%02d"' % (msg, t/60, t%60)
				convert.extend([oparen, '-clone', '0', caption, '-composite', cparen, '+page'])
			else:
				convert.append('text:"%s"' % tmp)
				convert.extend([oparen, '+clone', cparen, '+insert'])

		convert.append('ppm:-')

		cmd = '%s | %s' % (' '.join(convert), ' '.join(ffmpeg))
		sys.stderr.write("%s\n\n" % cmd)
		os.system(cmd)

#		image = Popen(convert, stdin=(PIPE if filemode else None), stdout=video.stdin)
#		if filemode:
#			image.communicate(input=msg)
#		image.wait()
#			video.stdin.flush()
#		video.terminate()


if __name__ == "__main__":

	import getopt

	def usage(err=None):
		if err: sys.stderr.write('%s\n' % err)
		sys.stderr.write( """
usage: vmsg [-m message | -i textfile] [-t seconds] [-c color] [-b background]
				[-f font] [-p pointsize] [-d density] [-g gravity] [-x]
				[-rate r] [-a imagefile] [-y image_gravity] [-z] [-o outputfile]
		 -x: show a countdown in seconds if -m is used
		 -z: resize the background image to fit the screen
"""		)
		sys.exit(1)

	try:
		opts = getopt.getopt(sys.argv[1:], "m:i:t:o:c:b:f:p:d:g:r:a:y:xz")
	except getopt.GetoptError, err:
		usage(err)

	keys = {
		'-m' : 'msg',
		'-i' : 'file',
		'-t' : 'seconds',
		'-o' : 'out',
		'-x' : 'countdown',
		'-c' : 'fill',
		'-b' : 'background',
		'-f' : 'font',
		'-p' : 'pointsize',
		'-d' : 'density',
		'-g' : 'gravity',
		'-r' : 'rate',
		'-a' : 'img',
		'-y' : 'imggrav',
		'-z' : 'resize',
#		'-s' : 'size'
	}

	kwargs = dict([(keys.get(k), v) for k, v in opts[0]])
	for i in ['seconds', 'pointsize', 'density']:
		if i in kwargs: kwargs[i] = int(kwargs[i])
	for i in ['countdown', 'resize']:
		if i in kwargs: kwargs[i] = True
	sys.stderr.write('args=%s\n' % kwargs)

	vmsg(**kwargs)


