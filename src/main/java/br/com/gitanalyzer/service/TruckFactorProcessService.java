package br.com.gitanalyzer.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import br.com.gitanalyzer.model.entity.TruckFactorProcess;
import br.com.gitanalyzer.repository.TruckFactorProcessRepository;
import br.com.gitanalyzer.repository.UserRepository;

@Service
public class TruckFactorProcessService {

	@Autowired
	private TruckFactorProcessRepository repository;
	@Autowired
	private UserRepository userRepository;

	public List<TruckFactorProcess> getByUserId(Long id) throws Exception{
		if(userRepository.existsById(id) == false) {
			throw new Exception("Usuário com id {"+id+"} não encontrado");
		}else {
			return repository.findByUserId(id);
		}
	}
}
