import umsgpack, json

with open('/dev/stdin', 'r') as stdin:
	with open('/dev/stdout', 'wb') as stdout:
	        print(umsgpack.pack(json.dumps(stdin.read()), stdout))
