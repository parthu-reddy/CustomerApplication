import os
import re

directory = '/Users/parthureddy/Documents/Food Delivery.nosync/CustomerApplication/src/main/java/com/fooddelivery'

for root, _, files in os.walk(directory):
    for file in files:
        if file.endswith('.java'):
            filepath = os.path.join(root, file)
            with open(filepath, 'r') as f:
                content = f.read()

            if ('@Service' in content or '@RestController' in content or '@Component' in content) and 'RequiredArgsConstructor' not in content:
                # Find the class declaration
                class_match = re.search(r'public class (\w+)', content)
                if class_match:
                    print(f"Fixing {file}")
                    # add @lombok.RequiredArgsConstructor above it
                    content = content[:class_match.start()] + '@lombok.RequiredArgsConstructor\n' + content[class_match.start():]
                    with open(filepath, 'w') as f:
                        f.write(content)

