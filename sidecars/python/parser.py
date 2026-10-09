import ast
import json
import sys


def extract_imports(tree):
    imports = []
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            # import a.b, c as d  ->  one entry per module
            for alias in node.names:
                imports.append({"module": alias.name, "names": [], "level": 0, "line": node.lineno})
        elif isinstance(node, ast.ImportFrom):
            # from ..pkg import a, b  ->  module="pkg", level=2, names=["a", "b"]
            imports.append({
                "module": node.module,
                "names": [alias.name for alias in node.names],
                "level": node.level or 0,
                "line": node.lineno,
            })
    return imports


def extract_ast(file_path):
    with open(file_path, "r", encoding="utf-8") as f:
        tree = ast.parse(f.read(), filename=file_path)

    return json.dumps({"file": file_path, "imports": extract_imports(tree), "error": None})


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(json.dumps({"error": "No file path provided"}))
        sys.exit(1)

    print(extract_ast(sys.argv[1]))
