def generate():
    return [{"action": "create", "source": "/test", "destination": "/dest"}]

print(str(generate()))
import json
print(json.dumps(generate()))
