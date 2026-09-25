import ast
import json
import sys

def extract_ast(file_path):
    with open(file_path, "r", encoding="utf-8") as f:
        tree = ast.parse(f.read(), filename=file_path)
    
    imports = []
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            for alias in node.names:
                imports.append({"type": "Import", "name": alias.name, "line": node.lineno})
        elif isinstance(node, ast.ImportFrom):
            imports.append({"type": "ImportFrom", "module": node.module, "line": node.lineno})
            
    return json.dumps({"file": file_path, "imports": imports})

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(json.dumps({"error": "No file path provided"}))
        sys.exit(1)
        
    print(extract_ast(sys.argv[1]))