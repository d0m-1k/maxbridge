import umsgpack

with open('/dev/stdin', 'rb') as stdin:
	print(umsgpack.unpack(stdin))
