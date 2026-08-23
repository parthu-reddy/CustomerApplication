import os
import glob

def remove_warnings():
    for filepath in glob.glob('/Users/parthureddy/Documents/Food Delivery.nosync/CustomerApplication/src/main/**/*.java', recursive=True):
        with open(filepath, 'r') as f:
            content = f.read()
        if '@SuppressWarnings("all")' in content or '@java.lang.SuppressWarnings("all")' in content:
            new_content = content.replace('@SuppressWarnings("all")', '').replace('@java.lang.SuppressWarnings("all")', '')
            with open(filepath, 'w') as f:
                f.write(new_content)
            print(f"Updated {filepath}")

remove_warnings()
