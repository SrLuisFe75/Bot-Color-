PY?=python3
PIP?=pip3
VENV=.venv

.PHONY: setup dry-run run format clean

setup:
	bash scripts/setup.sh

dry-run:
	$(PY) -m bot.main --dry-run

run:
	$(PY) -m bot.main

format:
	autopep8 -r --in-place bot scripts || true

clean:
	rm -rf __pycache__ bot/__pycache__ scripts/__pycache__ *.pyc *.pyo