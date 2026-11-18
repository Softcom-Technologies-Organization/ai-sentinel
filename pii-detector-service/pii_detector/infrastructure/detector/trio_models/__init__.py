def load_model(name: str, device: str):
    from .loaders import load_model as load

    return load(name, device)
