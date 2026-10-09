"""ArchGuard Python sidecar: prints the imports of one Python file as JSON.

Always exits 0 with a JSON object on stdout once it has a file to look at.
A file that cannot be parsed (syntax error, Python 2 code, bad encoding,
null bytes...) is reported in the "error" field instead of crashing, so one
broken file never fails a whole analysis job.
"""
import ast
import json
import sys
import warnings


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


def describe_error(exc):
    if isinstance(exc, SyntaxError) and exc.lineno:
        return f"{type(exc).__name__}: {exc.msg} (line {exc.lineno})"
    return f"{type(exc).__name__}: {exc}"


def extract_ast(file_path):
    try:
        # Read bytes so ast honours PEP 263 encoding declarations
        with open(file_path, "rb") as f:
            source = f.read()
        with warnings.catch_warnings():
            # e.g. SyntaxWarning for invalid escape sequences; not our concern
            warnings.simplefilter("ignore")
            tree = ast.parse(source, filename=file_path)
        result = {"file": file_path, "imports": extract_imports(tree), "error": None}
    except (SyntaxError, ValueError, UnicodeDecodeError, RecursionError, MemoryError, OSError) as exc:
        result = {"file": file_path, "imports": [], "error": describe_error(exc)}

    return json.dumps(result)


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(json.dumps({"file": None, "imports": [], "error": "No file path provided"}))
        sys.exit(2)

    print(extract_ast(sys.argv[1]))
