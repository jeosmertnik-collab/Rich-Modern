import os

java_dir = r'D:\Rich-Modern\src\main\java\excel'
count = 0
for root, dirs, files in os.walk(java_dir):
    for f in files:
        if not f.endswith('.java'):
            continue
        path = os.path.join(root, f)
        with open(path, 'r', encoding='utf-8', errors='ignore') as fh:
            content = fh.read()

        new_content = content.replace('Identifier.of("rich"', 'Identifier.of("excel"')

        if new_content != content:
            with open(path, 'w', encoding='utf-8') as fh:
                fh.write(new_content)
            count += 1

print('Updated Identifier.of references in %d files' % count)
